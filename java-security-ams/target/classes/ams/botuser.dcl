DEFAULT POLICY defaultBotUser {
    USE readAllOwnItems WHERE $user.user_uuid IN (
    'd9403e85-2029-46f1-9c09-ee32e881c081',
    '375cae4b-5a38-4009-a90f-b0002e661c8b',
    // ams-test
    'c08808e4-654e-4c42-a657-316e5b8fd286',
    // amseu10c
    '49edf608-c1a9-473c-9744-1c351925d877',
    // amstest
    '264da1f2-f0e7-4032-8331-0b10b0396ade');
}

TEST defaultBotUserTest {
    GRANT read POLICY defaultBotUser INPUT {
                $user:  { user_uuid: 'd9403e85-2029-46f1-9c09-ee32e881c081'},
                author: { createdBy: 'd9403e85-2029-46f1-9c09-ee32e881c081'}
            };
    DENY read POLICY defaultBotUser INPUT {
                $user:  { user_uuid: 'a9403e85-2022-44f1-9c09-ee32e881c083'},
                author: { createdBy: 'a9403e85-2022-44f1-9c09-ee32e881c083'}
            };
}