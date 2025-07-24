// SPDX-FileCopyrightText: 2020
// SPDX-License-Identifier: Apache-2.0

POLICY readAllSalesOrders {
GRANT read ON salesOrders WHERE Country IS NOT RESTRICTED;
}

POLICY readAllSales_Europe {
USE readAllSalesOrders RESTRICT Country IN('AT', 'BE', 'BG', 'HR', 'CY', 'DK', 'EE', 'FI', 'FR', 'DE', 'GR', 'HU', 'IE', 'IT', 'LV', 'LT', 'LU', 'MT', 'NL', 'PO', 'PT', 'RO', 'SK', 'SI', 'ES', 'SE');
}

TEST readAllSalesOrdersTest {
GRANT read ON salesOrders POLICY readAllSalesOrders;
DENY  read ON sales       POLICY readAllSalesOrders;
DENY  delete ON salesOrders POLICY readAllSalesOrders;
}

TEST readAllSales_EuropeTest {
DENY read on salesOrders POLICY readAllSales_Europe;
GRANT read on salesOrders POLICY readAllSales_Europe INPUT {
Country: 'LV'
};
DENY write on salesOrders POLICY readAllSales_Europe INPUT {
Country: 'LV'
};
DENY read on salesOrders POLICY readAllSales_Europe INPUT {
Country: 'US'
};
}
