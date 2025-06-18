// SPDX-FileCopyrightText: 2020
// SPDX-License-Identifier: Apache-2.0


POLICY adminAllSales {
// best practice
GRANT read, write, delete, activate ON salesOrders, salesOrderItems;
}

POLICY anyActionOnSales {
// '*' is considered harmful
GRANT * ON salesOrders, salesOrderItems;
}

POLICY readSalesOrders_Type {
GRANT read ON salesOrders WHERE salesOrder.type BETWEEN 100 AND 500;
}