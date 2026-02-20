// SPDX-FileCopyrightText: 2020
// SPDX-License-Identifier: Apache-2.0

TEST readSalesOrders_TypeTest {
GRANT read ON salesOrders POLICY readSalesOrders_Type INPUT {
salesOrder: {
type: 101
}
},{
salesOrder: {
type: 500
}
};

DENY read ON salesOrders POLICY readSalesOrders_Type INPUT {
salesOrder: {
type: 99
}
},{
salesOrder: {
type: 501
}
};
}
