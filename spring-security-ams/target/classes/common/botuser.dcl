
DEFAULT POLICY defaultBotUser {
    USE readAll_Europe WHERE $user.email IN (
        'dl_5eb27aaf4de077027e59aa60@global.corp.sap' );
}

TEST defaultBotUserTest {
    GRANT read ON * POLICY defaultBotUser INPUT {
                $user:  { email: 'dl_5eb27aaf4de077027e59aa60@global.corp.sap'},
                CountryCode: 'DE'
    };
}