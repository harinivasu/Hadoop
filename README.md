Hadoop
======

Learning Hadoop

while read line; do echo "<add>$line</add>" > temp.xml; java -Durl=http://10.74.132.32:8983/solr/collection1/update -jar /opt/apache/solr/solr-4.6.0/example/exampledocs/post.jar temp.xml; done < /opt/apache/solr/data/BusinessSolr/part-r-00000 > run.log &
