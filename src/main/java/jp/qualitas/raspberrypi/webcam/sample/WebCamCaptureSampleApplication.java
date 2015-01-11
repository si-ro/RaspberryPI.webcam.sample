package jp.qualitas.raspberrypi.webcam.sample;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamEvent;
import com.github.sarxos.webcam.WebcamException;
import com.github.sarxos.webcam.WebcamExceptionHandler;
import com.github.sarxos.webcam.WebcamListener;
import com.github.sarxos.webcam.WebcamResolution;
import com.github.sarxos.webcam.ds.v4l4j.V4l4jDriver;

public class WebCamCaptureSampleApplication extends Application implements
		WebcamListener {
	// set capture driver for v4l4j tool
	static {
		String osName = System.getProperty("os.name");
		String arch = System.getProperty("os.arch");
		if (osName.equals("Linux") & arch.equals("arm")) {
			Webcam.setDriver(new V4l4jDriver());
		}
	}
	/**
	 * Logger instance.
	 */
	private static final Logger LOG = LoggerFactory
			.getLogger(WebCamCaptureSampleApplication.class);
	/**
	 * Webcam
	 */
	private Webcam _capture;
	/**
	 * Scheduled executor acting as timer.
	 */
	private ScheduledExecutorService executor = null;
	/**
	 * Repainter is used to fetch images from camera and force panel repaint
	 * when image is ready.
	 */
	private ImageUpdater updater = null;
	/**
	 * Is frames requesting frequency limited? If true, images will be fetched
	 * in configured time intervals. If false, images will be fetched as fast as
	 * camera can serve them.
	 */
	private boolean frequencyLimit = false;
	/**
	 * Frames requesting frequency.
	 */
	private double frequency = 5; // FPS
	/**
	 * Image currently being displayed.
	 */
	private BufferedImage image = null;
	/**
	 * Webcam is currently starting.
	 */
	private volatile boolean starting = false;
	/**
	 * Is there any problem with webcam?
	 */
	private volatile boolean errored = false;
	/**
	 * Painting is paused.
	 */
	private volatile boolean paused = false;

	/**
	 * Webcam has been started.
	 */
	private final AtomicBoolean started = new AtomicBoolean(false);

	/**
	 * Webcam Image
	 */
	private ObjectProperty<Image> imageProperty = new SimpleObjectProperty<Image>();

	@Override
	public void start(Stage stage) throws Exception {
		Group root = new Group();
		Scene scene = new Scene(root, 640, 480);

		stage.setTitle("Webcam-capture Sample application.");

		ImageView imageView = new ImageView();
		imageView.setFitWidth(scene.getWidth());
		imageView.setFitHeight(scene.getHeight());
		imageView.imageProperty().bind(imageProperty);

		root.getChildren().add(imageView);
		stage.setScene(scene);
		stage.show();
		stage.setFullScreen(false);

		// キャプチャの準備
		try {
			this._capture = Webcam.getWebcams().get(1);
		} catch (Exception e) {
			this._capture = Webcam.getDefault();
		}
		Dimension size = WebcamResolution.QVGA.getSize();
		this._capture.setViewSize(size);
		this._capture.addWebcamListener(this);
		this.updater = new ImageUpdater();

		updater.start();

		this.starting = true;

		try {
			if (!this._capture.isOpen()) {
				errored = !this._capture.open();
			}
		} catch (WebcamException e) {
			errored = true;
			throw e;
		} finally {
			starting = false;
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		launch(args);
	}

	@Override
	public void webcamClosed(WebcamEvent arg0) {
		LOG.info("webcam closed.");

	}

	@Override
	public void webcamDisposed(WebcamEvent arg0) {
		LOG.info("webcam disposed.");
	}

	@Override
	public void webcamImageObtained(WebcamEvent arg0) {

	}

	@Override
	public void webcamOpen(WebcamEvent arg0) {
		LOG.info("webcam open.");
	}

	/**
	 * Is frequency limit enabled?
	 * 
	 * @return True or false
	 */
	public boolean isFPSLimited() {
		return frequencyLimit;
	}

	/**
	 * Image updater reads images from camera and force panel to be repainted.
	 * 
	 * @author Bartosz Firyn (SarXos)
	 */
	private class ImageUpdater implements Runnable {
		/**
		 * Repaint scheduler schedule panel updates.
		 * 
		 * @author Bartosz Firyn (sarxos)
		 */
		private class RepaintScheduler extends Thread {

			/**
			 * Repaint scheduler schedule panel updates.
			 */
			public RepaintScheduler() {
				setUncaughtExceptionHandler(WebcamExceptionHandler
						.getInstance());
				setName(String.format("repaint-scheduler-%s",
						_capture.getName()));
				setDaemon(true);
			}

			@Override
			public void run() {

				// do nothing when not running
				if (!running.get()) {
					return;
				}

				// loop when starting, to wait for images
				while (starting) {
					try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
						throw new RuntimeException(e);
					}
				}

				// schedule update when webcam is open, otherwise schedule
				// second scheduler execution

				try {

					// FPS limit means that panel rendering frequency is
					// limited to the specific value and panel will not be
					// rendered more often then specific value

					if (_capture.isOpen()) {

						// TODO: rename FPS value in panel to rendering
						// frequency

						if (isFPSLimited()) {
							executor.scheduleAtFixedRate(updater, 0,
									(long) (1000 / frequency),
									TimeUnit.MILLISECONDS);
						} else {
							executor.scheduleWithFixedDelay(updater, 100, 1,
									TimeUnit.MILLISECONDS);
						}
					} else {
						executor.schedule(this, 500, TimeUnit.MILLISECONDS);
					}
				} catch (RejectedExecutionException e) {

					// executor has been shut down, which means that someone
					// stopped panel / webcam device before it was actually
					// completely started (it was in "starting" timeframe)

					// LOG.warn("Executor rejected paint update");
					// LOG.trace("Executor rejected paint update because of",
					// e);

					return;
				}
			}
		}

		/**
		 * Update scheduler thread.
		 */
		private Thread scheduler = null;

		/**
		 * Is repainter running?
		 */
		private AtomicBoolean running = new AtomicBoolean(false);

		/**
		 * Start repainter. Can be invoked many times, but only first call will
		 * take effect.
		 */
		public void start() {
			if (running.compareAndSet(false, true)) {
				executor = Executors.newScheduledThreadPool(1, THREAD_FACTORY);
				scheduler = new RepaintScheduler();
				scheduler.start();
			}
		}

		/**
		 * Stop repainter. Can be invoked many times, but only first call will
		 * take effect.
		 * 
		 * @throws InterruptedException
		 */
		public void stop() throws InterruptedException {
			if (running.compareAndSet(true, false)) {
				// executor.shutdown();
				// executor.awaitTermination(5000, TimeUnit.MILLISECONDS);
				scheduler.join();
			}
		}

		public void run() {
			try {
				update();
			} catch (Throwable t) {
				errored = true;
				WebcamExceptionHandler.handle(t);
			}
		}

		private int index = 0;

		/**
		 * Perform single panel area update (repaint newly obtained image).
		 */
		private void update() {

			// do nothing when updater not running, when webcam is closed, or
			// panel repainting is paused

			if (!running.get() || !_capture.isOpen() || paused) {
				return;
			}

			// get new image from webcam

			BufferedImage tmp = _capture.getImage();
			boolean repaint = true;

			if (tmp != null) {

				// ignore repaint if image is the same as before
				if (image == tmp) {
					repaint = false;
				}
				if (repaint) {

					// WritableImage mainiamge = SwingFXUtils.toFXImage(image,
					// null);
					Platform.runLater(new Runnable() {
						@Override
						public void run() {
							WritableImage wr = null;
							BufferedImage bf = image;
							if (bf != null) {
								wr = new WritableImage(bf.getWidth(), bf
										.getHeight());
								PixelWriter pw = wr.getPixelWriter();
								for (int x = 0; x < bf.getWidth(); x++) {
									for (int y = 0; y < bf.getHeight(); y++) {
										pw.setArgb(x, y, bf.getRGB(x, y));
									}
								}
							}
							//imageView.setImage(wr);
							imageProperty.set(wr);
						}
					});

				}
				errored = false;
				image = tmp;
			}
		}
	}

	/**
	 * Thread factory used by execution service.
	 */
	private static final ThreadFactory THREAD_FACTORY = new PanelThreadFactory();

	private static final class PanelThreadFactory implements ThreadFactory {

		private static final AtomicInteger number = new AtomicInteger(0);

		public Thread newThread(Runnable r) {
			Thread t = new Thread(r, String.format(
					"webcam-panel-scheduled-executor-%d",
					number.incrementAndGet()));
			t.setUncaughtExceptionHandler(WebcamExceptionHandler.getInstance());
			t.setDaemon(true);
			return t;
		}
	}
}
