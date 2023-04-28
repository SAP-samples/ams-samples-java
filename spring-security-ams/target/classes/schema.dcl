// SPDX-FileCopyrightText: 2020
// SPDX-License-Identifier: Apache-2.0

// Schema can not be splitted, and CountryCode is required in multiple DCLs
schema {
    salesOrder: {
        type: number
    },
	CountryCode: string,
       // AdminService
    AdminService : {

        // AdminService.Books
        Books : {

            // genre.name : cds.String
            @valueHelp: {path: 'genre', valueField: 'ID', labelField: 'name'}
            genre_name : String,

            // author.name : cds.String
            author_name : String
        },

        // AdminService.Orders
        Orders : {

            // total : cds.Decimal
            total : Number,

            // currency.code : cds.String
            @valueHelp: {path: 'currency', valueField: 'ID', labelField: 'name'}
            currency_code : String
        },

        // AdminService.OrderItems
        OrderItems : {

            // amount : cds.Decimal
            amount : Number
        }
    },
    // ApproverService
    ApproverService : {

        // ApproverService.Orders
        Orders : {
            requestor : String
        }
    },
    // AutoApprove
    AutoApprove : {

        // ApproverService.Orders
        OrderItems : {
            amount : Number
        }
    }
}