using {sap.capire.bookshop as my} from '../db/schema';

service AdminService @(requires: ['ManageAuthors', 'ManageBooks']) {

  entity Books @(restrict: [
    { grant: ['READ'], to: 'ManageAuthors' },
    { grant: ['READ', 'WRITE'], to: 'ManageBooks' } ])
  as projection on my.Books;

  entity Authors @(restrict: [
    { grant: ['READ', 'WRITE'], to: 'ManageAuthors' },
    { grant: ['READ'], to: 'ManageBooks' } ])
  as projection on my.Authors;
}