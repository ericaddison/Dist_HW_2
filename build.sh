#!/bin/bash

mkdir build
javac -classpath ./lib/jackson-annotations-2.9.1.jar:./lib/jackson-databind-2.9.1.jar:./lib/jackson-core-2.9.1.jar:. -d ./build/ *.java

cd build
jar cvf ../HW2.jar *

cd ..
rm -rf build
