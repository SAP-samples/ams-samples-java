FROM --platform=linux/amd64 tomcat:10-jre17

COPY dcldeployer/dcl /dcl

COPY target/jakarta-ams-sample.war /usr/local/tomcat/webapps/ROOT.war

EXPOSE 8080
