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
						title: "/ams-spring-app/health",
						text: "GET /health checks whether the Policy Engine is reachable",
					},
					{
						title: "/ams-spring-app/authenticate",
						text: "GET /authenticate provides zone and id of authenticated user"
					},
					{
						title: "/ams-spring-app/read",
						text: "GET /read calls policyDecisionPoint.allow() to check whether your access is granted",
						description: "You will get a 403 (UNAUTHORIZED), in case you do not have permission for 'read' action (readAll policy)"
					},
					{
						title: "/ams-spring-app/authorized",
						text: "GET /authorized is protected with hasAuthority() Spring Security Expressions (Security Configuration)",
						description: "You will get a 403 (UNAUTHORIZED), in case you do not have permission for 'view' action (viewAll policy)"
					},
					{
						title: "/ams-spring-app/salesOrders",
						text: "GET /salesOrders is annotated with @PreAuthorize()",
						description: "You will get a 403 (UNAUTHORIZED), in case you do not have permission for 'read' action on sales orders (anyActionOnSales, adminAllSales policy)"
					},
					{
						title: "/ams-spring-app/salesOrders/readByCountry/IT",
						text: "GET /salesOrders/readByCountry/{countryCode} is annotated with @PreAuthorize()",
						description: "You will get a 403 (UNAUTHORIZED), in case you do not have permission for 'read' action on sales orders with the requested country code (readAll_Europe policy)"
					},
					{
						title: "AMS Admin UI - Maintain permissions",
						text: "You need to be an ias_admin within your IAS application. In the AMS Admin UI select 'webshop' (product_label)."
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
				let sUrl = "https://github.wdf.sap.corp/pages/CPSecurity/AMS/Overview/HowTo_AMSConfig/";
				window.open(sUrl, "_blank", "noopener");
			},

			onNavToAmsUi: function() {
				var xmlHttp = new XMLHttpRequest();
				xmlHttp.open( "GET", "/ams-spring-app/uiurl", false );

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
