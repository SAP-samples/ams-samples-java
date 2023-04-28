DEFAULT POLICY defaultBotUser {
    USE readAllOwnItems RESTRICT $user.user_uuid IN (
    'd9403e85-2029-46f1-9c09-ee32e881c081',
    '375cae4b-5a38-4009-a90f-b0002e661c8b',
    // ams-test
    'c08808e4-654e-4c42-a657-316e5b8fd286',
    // amseu10c
    '49edf608-c1a9-473c-9744-1c351925d877',
    // amstest
    '264da1f2-f0e7-4032-8331-0b10b0396ade',
    // amstest-au
    'd0b16aad-4e8c-4f5e-a3ab-35cc5a806afa',
    // amstest-us
    '763ac941-f737-4b4f-acdf-f69db81dfa57',
    // amstest-in-prod
    'eb19c6d7-8902-409b-ad73-301246bf7249',
    // amstest-kr-prod
    '4af65e8b-a436-4214-93e9-3d3667fbabc5',
    // amstest-br-prod
    'c4f970d4-08a2-478c-bbbe-6cae0d00f093',
    // amstest-prod-sgp
    '1a15c4a5-f1e9-452f-9194-da122f3d6ae7',
    // amstest-uae-prod
    'f05a55c4-1280-4c84-a225-a2b156f50293',
    // amstest-ch-prod
    '4acaa212-5693-4b45-883a-1593b14512fb',
    // amstest-jp-prod
    '01967a84-f696-409d-b6e6-37bba6de5109',
    // amstest-useast-prod
    '70202c1d-97a8-4b44-91ee-b1b83a21544c',
    // amstest-au-prod
    '2d2337b2-bf37-4f2d-a724-33f513eeb8d4',
    // amstest-cn-prod
    '58c0a011-0248-47db-b056-43549bcb393b',
    // amstest-ca-prod
    '8376f70e-be07-4431-b362-e7ef940e0c57',
    // amstest-uswe-prod
    '2e9c0751-64cd-42e4-9a7e-277bd9e8095f'
    );
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
