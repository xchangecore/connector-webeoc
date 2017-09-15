#!/bin/sh

mvn install:install-file -DgroupId=com.saic.uicds.clients.em.webeoc -DartifactId=com.saic.uicds.clients.em.webeoc.xmlbeans -Dversion=1.0 -Dpackaging=jar -Dfile=./jarfiles/webeoc-xmlbeans.jar

