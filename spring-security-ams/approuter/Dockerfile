# base image build time
FROM docker.wdf.sap.corp:50001/com.sap.html5.apps/approuter-10.6:10.6.0

WORKDIR /app

# copy stuff
COPY webapp webapp
COPY static static
COPY xs-app.json xs-app.json
COPY package.json package.json