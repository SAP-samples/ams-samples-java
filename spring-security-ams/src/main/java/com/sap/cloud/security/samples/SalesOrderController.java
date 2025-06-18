/*
 * SPDX-FileCopyrightText: 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.sap.cloud.security.samples;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/salesOrders")
public class SalesOrderController {

    /**
     * An endpoint showing how to use Spring method security. Only if the request principal has the given privilege
     * he/she is allowed to access the method. Otherwise a 403 error will be returned.
     */
    @PreAuthorize("forResourceAction('salesOrders', 'read')")
    @GetMapping
    public String readSelectedSalesOrder() {
        return "Read-protected salesOrders resource accessed!";
    }

    @PreAuthorize("forAction('read', 'CountryCode:string=' + #countryCode)")
    @GetMapping(value = "/readByCountry/{countryCode}")
    public String readResourcesInCountry(@PathVariable String countryCode) {
        return "Read-protected resource with countryCode = " + countryCode + " accessed!";
    }

    @PreAuthorize("forResourceAction('salesOrders', 'read', 'CountryCode:string=' + #countryCode, 'salesOrder.type:number=' + #type)")
    @GetMapping(value = "/readByCountryAndType/{countryCode}/{type}")
    public String readSelectedSalesOrder(@PathVariable String countryCode, @PathVariable String type) {
        return "Read-protected SalesOrder with attributes countryCode = " + countryCode + " and type " + type
                + " accessed!";
    }
}