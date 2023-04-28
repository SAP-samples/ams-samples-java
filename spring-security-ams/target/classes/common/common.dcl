// SPDX-FileCopyrightText: 2020
// SPDX-License-Identifier: Apache-2.0

POLICY viewAll { // only for trivial applications
	GRANT view ON *;
}

POLICY readAll {
	GRANT read ON * WHERE CountryCode IS NOT RESTRICTED;
}

POLICY readAll_Europe {
	USE readAll RESTRICT CountryCode IN ('AT', 'BE', 'BG', 'HR', 'CY', 'DK', 'EE', 'FI', 'FR', 'DE',
					'GR', 'HU', 'IE', 'IT', 'LV', 'LT', 'LU', 'MT', 'NL', 'PO', 'PT', 'RO', 'SK', 'SI', 'ES', 'SE');
}