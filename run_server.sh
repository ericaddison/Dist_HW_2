file=$1
restart=$2
java -classpath ./lib/jackson-annotations-2.9.1.jar:./lib/jackson-databind-2.9.1.jar:./lib/jackson-core-2.9.1.jar:. Server $file $restart
