package agora.rest.stream

/**
  * consider a feed of:
  *
  * 'item.bestPriceLocation = "amazon"'
  *
  * and we want to join a feed of:
  *
  * 'storeId' # e.g. 'amazon'
  * 'bestUserRatingFrom' # e.g. 'bob'
  *
  * and
  *
  * 'user.name' # e.g 'bob'
  * 'favouriteFood'
  *
  * Where we want to present a table of:
  *
  * {{{
  * Item Location | Best UserRating | Favourite Food |
  * }}}
  *
  * upon each update of 'item' for a particular entry, we need to get the field 'bestUserRatingFrom' from a
  * stream where storeId, and then from that feed the 'favouriteFood'.
  *
  *
  * So it would seem for a particular data stream, we want to dynamically send it updated filters and fields
  *
  */
object Joins {}
