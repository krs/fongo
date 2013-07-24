package com.foursquare.fongo.impl.aggregation;

import com.foursquare.fongo.impl.Util;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.FongoDB;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * User: william
 * Date: 24/07/13
 */
public class Group extends PipelineKeyword {
  private static final Logger LOG = LoggerFactory.getLogger(Group.class);

  public Group(FongoDB fongoDB) {
    super(fongoDB);
  }

  public DBCollection apply(DBCollection coll, DBObject object) {
    DBObject group = (DBObject) object.get(getKeyword());
    // $group : { _id : "0", "$max":"$date" }
    // $group: { _id: "$department", average: { $avg: "$amount" } }
    List<DBObject> objects = new ArrayList<DBObject>();
    for (Map.Entry<String, Object> entry : ((Set<Map.Entry<String, Object>>) group.toMap().entrySet())) {
      String key = entry.getKey();
      if (!key.equals("_id")) {
        Object value = entry.getValue();
        if (value instanceof DBObject) {
          DBObject objectValue = (DBObject) value;
          Object result = null;
          boolean nullForced = false;
          if (objectValue.containsField("$min")) {
            result = minmax(coll, objectValue.get("$min"), 1);
          } else if (objectValue.containsField("$max")) {
            result = minmax(coll, objectValue.get("$max"), -1);
          } else if (objectValue.containsField("$last")) {
            result = firstlast(coll, objectValue.get("$last"), false);
            nullForced = true;
          } else if (objectValue.containsField("$first")) {
            result = firstlast(coll, objectValue.get("$first"), true);
            nullForced = true;
          } else if (objectValue.containsField("$avg")) {
            result = avg(coll, objectValue.get("$avg"));
          } else if (objectValue.containsField("$sum")) {
            result = sum(coll, objectValue.get("$sum"));
          }

          if (result != null || nullForced) {
            objects.add(new BasicDBObject(key, result));
            LOG.debug("key:{}, result:{}", key, result);
          } else {
            LOG.warn("result is null for entry {}", entry);
          }
        }
      }
    }
    coll = dropAndInsert(coll, objects);
    LOG.debug("group : {} result : {}", object, objects);
    return coll;
  }

  /**
   * {@see http://docs.mongodb.org/manual/reference/aggregation/sum/#grp._S_sum}
   *
   * @param coll
   * @param value
   * @return
   */
  private Object sum(DBCollection coll, Object value) {
    Number result = null;
    if (value.toString().startsWith("$")) {
      String field = value.toString().substring(1);
      List<DBObject> objects = coll.find(null, new BasicDBObject(field, 1).append("_id", 0)).toArray();
      for (DBObject object : objects) {
        LOG.debug("sum object {} ", object);
        if (Util.containsField(object, field)) {
          if (result == null) {
            result = Util.extractField(object, field);
          } else {
            Number other = Util.extractField(object, field);
            if (result instanceof Float) {
              result = Float.valueOf(result.floatValue() + other.floatValue());
            } else if (result instanceof Double) {
              result = Double.valueOf(result.doubleValue() + other.doubleValue());
            } else if (result instanceof Integer) {
              result = Integer.valueOf(result.intValue() + other.intValue());
            } else if (result instanceof Long) {
              result = Long.valueOf(result.longValue() + other.longValue());
            } else {
              LOG.warn("type of field not handled for sum : {}", result.getClass());
            }
          }
        }
      }
    } else {
      int iValue = Integer.parseInt(value.toString());
      // TODO : handle null value ?
      result = coll.count() * iValue;
    }
    return result;
  }

  /**
   * {@see http://docs.mongodb.org/manual/reference/aggregation/avg/#grp._S_avg}
   *
   * @param coll
   * @param value
   * @return
   */
  private Object avg(DBCollection coll, Object value) {
    Number result = null;
    long count = 1;
    if (value.toString().startsWith("$")) {
      String field = value.toString().substring(1);
      List<DBObject> objects = coll.find(null, new BasicDBObject(field, 1).append("_id", 0)).toArray();
      for (DBObject object : objects) {
        LOG.debug("avg object {} ", object);

        if (Util.containsField(object, field)) {
          if (result == null) {
            result = Util.extractField(object, field);
          } else {
            count++;
            Number other = Util.extractField(object, field);
            if (result instanceof Float) {
              result = Float.valueOf(result.floatValue() + other.floatValue());
            } else if (result instanceof Double) {
              result = Double.valueOf(result.doubleValue() + other.doubleValue());
            } else if (result instanceof Integer) {
              result = Integer.valueOf(result.intValue() + other.intValue());
            } else if (result instanceof Long) {
              result = Long.valueOf(result.longValue() + other.longValue());
            } else {
              LOG.warn("type of field not handled for avg : {}", result.getClass());
            }
          }
        }
      }
    } else {
      LOG.error("Sorry, doesn't know what to do...");
      return null;
    }
    return result.doubleValue() / count;
  }

  /**
   * @param coll
   * @param value
   * @return
   */
  private Object firstlast(DBCollection coll, Object value, boolean first) {
    LOG.debug("first({})/last({}) on {}", first, !first, value);
    Object result = null;
    if (value.toString().startsWith("$")) {
      String field = value.toString().substring(1);
      List<DBObject> objects = coll.find(null, new BasicDBObject(field, 1).append("_id", 0)).toArray();
      for (DBObject object : objects) {
        result = Util.extractField(object, field);
        ;
        if (first) {
          break;
        }
      }
    } else {
      LOG.error("Sorry, doesn't know what to do...");
    }

    LOG.debug("first({})/last({}) on {}, result : {}", first, !first, value, result);
    return result;
  }

  /**
   * @param coll
   * @param value
   * @param valueComparable 0 for equals, -1 for min, +1 for max
   * @return
   */
  private Object minmax(DBCollection coll, Object value, int valueComparable) {
    if (value.toString().startsWith("$")) {
      String field = value.toString().substring(1);
      List<DBObject> objects = coll.find(null, new BasicDBObject(field, 1).append("_id", 0)).toArray();
      Comparable compable = null;
      for (DBObject object : objects) {
        LOG.debug("minmax object {} ", object);
        if (Util.containsField(object, field)) {
          if (compable == null) {
            compable = Util.extractField(object, field);
          } else {
            Comparable other = Util.extractField(object, field);
            LOG.trace("minmax {} vs {}", compable, other);
            if (compable.compareTo(other) == valueComparable) {
              compable = other;
            }
          }
        }
      }
      return compable;
    } else {
      LOG.error("Sorry, doesn't know what to do...");
    }
    return null;
  }

  @Override
  public String getKeyword() {
    return "$group";
  }

}
