using {
  Currency,
  managed,
  cuid,
  sap.common.CodeList
} from '@sap/cds/common';

namespace sap.capire.bookshop;

@ams.attributes: {
  Genre: (genre.name)
}
aspect withGenre {
    genre : Association to Genres;
}

entity Books : managed, cuid, withGenre {
  @mandatory title  : localized String(111);
  descr             : localized String(1111);
  @mandatory author : Association to Authors;
  stock             : Integer;
  price             : Decimal;
  currency          : Currency;
  image             : LargeBinary @Core.MediaType: 'image/png';
}

entity Authors : managed, cuid {
  @mandatory name : String(111);
  dateOfBirth     : Date;
  dateOfDeath     : Date;
  placeOfBirth    : String;
  placeOfDeath    : String;
  books           : Association to many Books
                      on books.author = $self;
}

/** Hierarchically organized Code List for Genres */
entity Genres : CodeList {
  key ID       : Integer;
      parent   : Association to Genres;
      children : Composition of many Genres
                   on children.parent = $self;
}
