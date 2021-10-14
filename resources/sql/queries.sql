/*
**SQL File Conventions (as of hugsql "0.5.1")

****HugSQL recognizes the following keys:

    :name or :name- (private fn) = name of the function to create and,
    optionally, the command and result as a shorthand in place of providing
    these as separate key/value pairs

    :doc = docstring for the created function

    :command = underlying database command to run

    :result = expected result type

    :snip or :snip- (private fn) = name of the function to create and,
    optionally, the command and result as a shorthand in place of providing
    these as separate key/value pairs. :snip is used in place of :name for
    snippets.

    :meta metadata in the form of an EDN hashmap to attach to function

    :require = namespace require and aliases for Clojure expression support

****Command

  The :command specifies the underlying database command to run for the given
  SQL. The built-in values are:

    :query or :? = query with a result-set (default)

    :execute or :! = any statement

    :returning-execute or :<! = support for INSERT ... RETURNING

    :insert or :i! = support for insert and jdbc .getGeneratedKeys

    :query and :execute mirror the distinction between query and execute! in
    the clojure.java.jdbc library and fetch and execute in the clojure.jdbc
    library.

    :query is the default command when no command is specified.

****Result

  The :result specifies the expected result type for the given SQL.
  The available built-in values are:

    :one or :1 = one row as a hash-map

    :many or :* = many rows as a vector of hash-maps

    :affected or :n = number of rows affected (inserted/updated/deleted)

    :raw = passthrough an untouched result (default)

    :raw is the default when no result is specified.
*/

-- :name get-trips-history :? :*
SELECT 
  trips.truckname, 
  trips.utcunload, 
  trips.utcloadstart, 
  DECODE ( 
    trips.utcloadend, 
    NULL, trips.gmttimeload, 
    trips.utcloadend 
  ) AS utcloadend, 
  trips.weightunload, 
  ( 
    SELECT 
      NVL(MAX(weight), 0) AS weight 
    FROM 
      dispatcher.eventstatearchive 
    WHERE 
      vehid = trips.truckname 
      AND gmttime = TO_DATE(trips.utcloadend, 'dd.mm.yyyy hh24:mi:ss') 
  ) AS weightload 
FROM ( 
  SELECT DISTINCT 
    TO_CHAR(gmttimego, 'dd.mm.yyyy hh24:mi:ss') AS utcloadend, 
    vehtrips.vehid AS truckname, 
    TO_CHAR(gmttimeunload, 'dd.mm.yyyy hh24:mi:ss') AS utcunload, 
    TO_CHAR(gmttimeload, 'dd.mm.yyyy hh24:mi:ss') AS gmttimeload, 
    TO_CHAR( 
      gmttimeload - :minloadtime / 60.0 / 24.0, 
      'dd.mm.yyyy hh24:mi:ss' 
    ) AS utcloadstart, 
    weight AS weightunload 
  FROM 
    dispatcher.vehtrips 
    INNER JOIN 
      dispatcher.shovels 
      ON shovels.shovid = vehtrips.shovid 
    LEFT JOIN 
      dispatcher.idlestoppages 
      ON idlestoppages.vehid = vehtrips.vehid 
  WHERE 
    gmttimeload BETWEEN TO_DATE(:utcfrom, 'dd.mm.yy hh24:mi:ss') 
                AND TO_DATE(:utcto, 'dd.mm.yy hh24:mi:ss') 
    AND shovels.controlid = :shovcontrolid 
    AND gmttimeload BETWEEN gmttimestop AND gmttimego 
    AND idlestoptypeauto = :idlestoptype 
  ) trips