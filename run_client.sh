file=$1
java -classpath ./lib/jackson-annotations-2.9.1.jar:./lib/jackson-databind-2.9.1.jar:./lib/jackson-core-2.9.1.jar:HW2.jar:. Client $file
