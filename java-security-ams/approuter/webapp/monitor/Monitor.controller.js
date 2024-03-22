sap.ui.define([
	"sap/ui/core/mvc/Controller",
	"sap/ui/model/json/JSONModel"
],
	/**
	 * @param {typeof sap.ui.core.mvc.Controller} Controller
	 */
	function (Controller, JSONModel) {
		"use strict";

		return Controller.extend("monitor.monitor.monitor.Monitor", {
			onInit: function () {
				this.getView().setModel(new JSONModel([
					{
						title: "/ams-java-app/health",
						text: "GET /health checks whether the Policy Engine is reachable",
					},
					{
						title: "/ams-java-app/app/hello-java-security",
						text: "GET /read protected endpoint for salesOrders resource",
						description: "You will get a 403 (UNAUTHORIZED), in case you do not have permission for 'read' action (readAllSalesOrders policy)"
					},
					{
						title: "/ams-java-app/app/java-security",
						text: "GET /Read-protected endpoint. Retrieves access constraints",
						description: "You will get a 403 (UNAUTHORIZED), in case you do not have permission for 'read' action (readAll policy)"
					},
					{
						title: "AMS Admin UI - Maintain permissions",
						text: "Link to your IAS Admin Console"
					},
					{
						title: "Link to Knowledge Base",
						text: "Gain further Knowledge, e.g. on how to create an admin user for your AMS tenant."
					}
				]), "links");
			},

			onNavToLink: function(oEvent) {
				let sUrl = oEvent.getSource().getTitle();
				window.open(sUrl, "_blank", "noopener");
			},

			onNavToKnowledgeBase: function () {
				let sUrl = "https://go.sap.corp/amsauthz";
				window.open(sUrl, "_blank", "noopener");
			},

			onNavToAmsUi: function() {
				var xmlHttp = new XMLHttpRequest();
				xmlHttp.open( "GET", "/ams-java-app/uiurl", false );

				xmlHttp.onload = function() {
					window.open(xmlHttp.responseText, "_blank", "noopener");	
				};
				xmlHttp.send( null );
			},

			onLogout: function() {
				sap.m.URLHelper.redirect("/logout");
			}
		});
	});
