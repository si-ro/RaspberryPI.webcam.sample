# RaspberryPI WebCam Sample application.
Webcam example JavaFX application for Raspberry PI.
This application use the sarxos's Webcam-Capture Library(https://github.com/sarxos/webcam-capture) and Video4Linux API.

## Setup
### Setup JDK8 and JavaFX
#### Download JDK8.
http://www.oracle.com/technetwork/java/javase/downloads/jdk8-arm-downloads-2187472.html
#### Install
``` bash
tar xvzf jdk-8u6-linux-arm-vfp-hflt.gz
sudo mv jdk1.8.0_06/ /usr/lib/jvm/
export JAVA_HOME=/usr/lib/jvm/jdk1.8.0_06/
export JDK_HOME=/usr/lib/jvm/jdk1.8.0_06/
```
### Setup v4l4j
https://code.google.com/p/v4l4j/
``` bash
# Install what's required to build v4l4j with
sudo apt-get install ant libjpeg8-dev libv4l-dev
sudo apt-get remove openjdk-6-*
# Check out a copy of v4l4
svn co http://v4l4j.googlecode.com/svn/v4l4j/trunk v4l4j-trunk
cd v4l4j-trunk
# build and install
ant clean all
sudo ant install
# test
java -cp /usr/share/java/v4l4j.jar -Djava.library.path=/usr/lib/jni au.edu.jcu.v4l4j.examples.DumpInfo
```
## Run
Run sample application using javafx-maven-plugin.  

``` bash
# Using git, fetch sample applicaton source. 
git clone https://github.com/si-ro/RaspberryPI.webcam.sample.git
# Move the repository. 
cd RaspberryPI.webcam.sample
# run application.
export MAVEN_OPTS="-Djava.library.path=/usr/lib/jni"
mvn jfx:run
```
