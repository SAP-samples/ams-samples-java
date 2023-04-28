
DEFAULT POLICY defaultBotUser {
    USE readAll RESTRICT 
        CountryCode IN ('AT', 'BE', 'BG', 'HR', 'CY', 'DK', 'EE', 'FI', 'FR', 'DE', 'GR', 'HU', 'IE', 'IT', 'LV', 'LT', 'LU', 'MT', 'NL', 'PO', 'PT', 'RO', 'SK', 'SI', 'ES', 'SE'),
        $user.email IN ('dl_5eb27aaf4de077027e59aa60@global.corp.sap');
}

TEST defaultBotUserTest {
    GRANT read ON * POLICY defaultBotUser INPUT
        {$user: { email: 'dl_5eb27aaf4de077027e59aa60@global.corp.sap'}, CountryCode: 'DE'},
        {$user: { email: 'dl_5eb27aaf4de077027e59aa60@global.corp.sap'}, CountryCode: 'IT'},
        {$user: { email: 'dl_5eb27aaf4de077027e59aa60@global.corp.sap'}, CountryCode: 'GR'};


    DENY read ON * POLICY defaultBotUser INPUT
        {$user: { email: 'dl_5eb27aaf4de077027e59aa60@global.corp.sap'}, CountryCode: 'US'},
        {CountryCode: 'DE'},
        {CountryCode: 'IT'},
        {CountryCode: 'GR'};
}