// SPDX-FileCopyrightText: 2020
// SPDX-License-Identifier: Apache-2.0

TEST readAllOwnItems {
GRANT read ON item POLICY readAllOwnItems INPUT {
$user:  {
user_uuid: 'author-user-id'
}, author: {
createdBy: 'author-user-id'
}
};

GRANT read POLICY readAllOwnItems INPUT {
$user:  {
user_uuid: 'author-user-id'
}, author: {
createdBy: 'author-user-id'
}
};

DENY read POLICY readAllOwnItems INPUT {
$user:  {
user_uuid: 'dummy-user-id'
}, author: {
createdBy: 'author-user-id'
}
};
}

TEST readAllOwnItems_new {
GRANT read POLICY readAllOwnItems_new INPUT {
$user:  {
user_uuid: 'author-user-id'
}, author: {
createdBy: 'author-user-id'
}

},{
$user:  {
user_uuid: 'author-user-id'
}, author: {
updatedBy: 'author-user-id'
}

},{
$user:  {
user_uuid: 'dummy-user-id'
}, author: {
createdBy: 'author-user-id',
updatedBy: 'dummy-user-id'
}
},{
$user:  {
user_uuid: 'author-user-id'
}, author: {
createdBy: 'author-user-id',
updatedBy: 'dummy-user-id'
}
};

DENY read POLICY readAllOwnItems_new INPUT {
$user:  {
user_uuid: 'dummy-user-id'
}, author: {
createdBy: 'author-user-id'
}
},{
$user:  {
user_uuid: 'dummy-user-id'
}, author: {
updatedBy: 'author-user-id'
}
},{
$user:  {
user_uuid: 'dummy-user-id'
}, author: {
createdBy: 'author-user-id',
updatedBy: 'author-user-id'
}
};
}
