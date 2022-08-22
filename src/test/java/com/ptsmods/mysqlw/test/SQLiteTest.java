package com.ptsmods.mysqlw.test;

import com.ptsmods.mysqlw.Database;
import com.ptsmods.mysqlw.collection.DbList;
import com.ptsmods.mysqlw.collection.DbMap;
import com.ptsmods.mysqlw.collection.DbSet;
import com.ptsmods.mysqlw.query.*;
import com.ptsmods.mysqlw.table.ColumnType;
import com.ptsmods.mysqlw.table.TableIndex;
import com.ptsmods.mysqlw.table.TablePreset;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("ALL")
@TestMethodOrder(MethodOrderer.Alphanumeric.class)
class SQLiteTest {
    private static final UUID testId = UUID.nameUUIDFromBytes("MySQLw".getBytes(StandardCharsets.UTF_8));
    private static Database db = null;

    Database getDb() throws SQLException {
        if (db == null) {
            db = Database.connect(new File("sqlite.db"));
            db.setLogging(false);

            System.out.println(TablePreset.create("join_test_1")
                    .putColumn("id", ColumnType.INT.struct()
                            .configure(sup -> sup.apply(null))
                            .setAutoIncrement()
                            .setPrimary()
                            .setNonNull())
                    .putColumn("value1", ColumnType.TEXT.struct())
                    .buildQuery(Database.RDBMS.SQLite));

            TablePreset.create("join_test_1")
                    .putColumn("id", ColumnType.INT.struct()
                            .configure(sup -> sup.apply(null))
                            .setAutoIncrement()
                            .setPrimary()
                            .setNonNull())
                    .putColumn("value1", ColumnType.TEXT.struct())
                    .create(db);

            TablePreset.create("join_test_2")
                    .putColumn("id", ColumnType.INT.struct()
                            .configure(sup -> sup.apply(null))
                            .setAutoIncrement()
                            .setPrimary()
                            .setNonNull())
                    .putColumn("value2", ColumnType.UUID.struct())
                    .create(db);

            if (db.count("join_test_1", "*", null) == 0) {
                db.insert("join_test_1", "value1", "Value from table 1");
                db.insert("join_test_2", "value2", testId);
            }
        }

        return db;
    }

    @Test
    void _loadConnector() {
        assertDoesNotThrow(() -> Database.loadConnector(Database.RDBMS.SQLite, null, new File("sqlite-connector.jar"), true));
    }

    @Test
    void connect() {
        assertDoesNotThrow(this::getDb);
    }

    @Test
    void testMap() throws SQLException {
        DbMap<String, Integer> map = DbMap.getMap(getDb(), "testmap", String.class, Integer.class);
        map.clear();
        assertEquals(0, map.size());
        map.put("testkey", 42);
        assertTrue(map.containsKey("testkey"));
        assertEquals(42, map.get("testkey"));
        assertEquals(1, map.size());
        map.clear();
        assertTrue(map.isEmpty());
    }

    @Test
    void testList() throws SQLException {
        DbList<String> list = DbList.getList(getDb(), "testlist", String.class);
        assertEquals(0, list.size());
        list.add("Hello");
        assertEquals(1, list.size());
        assertEquals("Hello", list.get(0));
        assertTrue(list.contains("Hello"));
        list.addAll(Arrays.asList("test", "test2"));
        assertTrue(list.containsAll(Arrays.asList("test", "test2")));
        list.clear();
        assertTrue(list.isEmpty());
    }

    @Test
    void testSet() throws SQLException {
        DbSet<String> set = DbSet.getSet(getDb(), "testset", String.class);
        assertEquals(0, set.size());
        set.add("hey");
        set.add("Hello");
        assertEquals(2, set.size());
        assertTrue(set.contains("hey"));
        set.addAll(Arrays.asList("test", "test2"));
        assertTrue(set.containsAll(Arrays.asList("test", "test2")));
        set.clear();
        assertTrue(set.isEmpty());
    }

    @Test
    void count() throws SQLException {
        assertEquals(2, getDb().count("testtable", "*", null));
    }

    @Test
    void truncate() throws SQLException {
        getDb().truncate("testtable");
        assertEquals(0, getDb().count("testtable", "*", null));
        getDb().insert("testtable", new String[] {"keyword", "value"}, Arrays.asList(new Object[] {"key1", "val1"}, new Object[] {"key2", "val2"}));
    }

    @Test
    void delete() throws SQLException {
        assertEquals(1, getDb().delete("testtable", QueryCondition.equals("keyword", "key2")));
        getDb().insert("testtable", new String[] {"keyword", "value"}, new Object[] {"key2", "val2"});
    }

    @Test
    void select() throws SQLException {
        assertEquals(2, getDb().select("testtable", "*", QueryConditions.create(QueryCondition.equals("keyword", "key2")).or(QueryCondition.equals("value", "val1")), null, null).size());
        assertEquals(1, getDb().select("testtable", "*", QueryCondition.equals("keyword", "key2"), null, null).size());
    }

    @Test
    void insert() throws SQLException {
        assertEquals(0, getDb().select("testtable", "*", QueryCondition.equals("keyword", "key3"), null, null).size());
        getDb().insert("testtable", new String[] {"keyword", "value"}, new Object[] {"key3", "val3"});
        assertEquals(1, getDb().select("testtable", "*", QueryCondition.equals("keyword", "key3"), null, null).size());
        assertEquals(0, getDb().select("testtable", "*", QueryConditions.create(QueryCondition.equals("keyword", "key4")).or(QueryCondition.equals("keyword", "key5")), null, null).size());
        getDb().insert("testtable", new String[] {"keyword", "value"}, Arrays.asList(new Object[] {"key4", "val4"}, new Object[] {"key5", "val5"}));
        assertEquals(2, getDb().select("testtable", "*", QueryConditions.create(QueryCondition.equals("keyword", "key4")).or(QueryCondition.equals("keyword", "key5")), null, null).size());
        QueryCondition condition = QueryConditions.create(QueryCondition.equals("keyword", "key3")).or(QueryCondition.equals("keyword", "key4")).or(QueryCondition.equals("keyword", "key5"));
        getDb().delete("testtable", condition);
    }

    @Test
    void insertDuplicate() throws SQLException {
        assertEquals("val2", getDb().select("testtable", "value", QueryCondition.equals("keyword", "key2"), null, null).get(0).get("value"));
        assertEquals(1, getDb().insertUpdate("testtable", new String[] {"keyword", "value"}, new Object[] {"key2", "val2"}, Database.singletonMap("value", "val6"), "keyword"));
        assertEquals("val6", getDb().select("testtable", "value", QueryCondition.equals("keyword", "key2"), null, null).get(0).get("value"));
        assertEquals(1, getDb().insertUpdate("testtable", new String[] {"keyword", "value"}, new Object[] {"key2", "val2"}, Database.singletonMap("value", "val2"), "keyword"));
    }

    @Test
    void update() throws SQLException {
        assertEquals("val2", getDb().select("testtable", "value", QueryCondition.equals("keyword", "key2"), null, null).get(0).get("value"));
        getDb().update("testtable", "value", "val8", QueryCondition.equals("keyword", "key2"));
        assertEquals("val8", getDb().select("testtable", "value", QueryCondition.equals("keyword", "key2"), null, null).get(0).get("value"));
        getDb().update("testtable", "value", "val2", QueryCondition.equals("keyword", "key2"));
    }

    @Test
    void replace() throws SQLException {
        assertEquals("val2", getDb().select("testtable", "value", QueryCondition.equals("keyword", "key2"), null, null).get(0).get("value"));
        getDb().replace("testtable", new String[] {"keyword", "value"}, new Object[] {"key2", "val10"});
        assertEquals("val10", getDb().select("testtable", "value", QueryCondition.equals("keyword", "key2"), null, null).get(0).get("value"));
        getDb().replace("testtable", new String[] {"keyword", "value"}, new Object[] {"key2", "val2"});
    }

    @Test
    void createTable() throws SQLException {
        assertFalse(getDb().tableExists("temptable"));
        TablePreset.create("temptable").putColumn("var", ColumnType.TEXT.struct()).create(getDb());
        assertTrue(getDb().tableExists("temptable"));
        getDb().drop("temptable");
        assertFalse(getDb().tableExists("temptable"));
    }

    @Test
    void tableExists() throws SQLException {
        assertTrue(getDb().tableExists("testtable"));
        assertFalse(getDb().tableExists("nonexistent"));
    }

    @Test
    void readQuotedString() {
        assertEquals("This is a name", Database.readQuotedString("'This is a name',values={}]"));
    }

    @Test
    void typeConverter() throws SQLException {
        Database.registerTypeConverter(UUID.class, id -> id == null ? null : Database.enquote(id.toString()), UUID::fromString);
        TablePreset.create("typetest").putColumn("id", ColumnType.CHAR.struct().configure(sup -> sup.apply(36))).create(getDb());
        getDb().truncate("typetest");
        UUID id = UUID.randomUUID();
        assertEquals(1, getDb().insert("typetest", "id", id));
        assertEquals(id, getDb().select("typetest", "id").get(0).get("id", UUID.class));
    }


    @Test
    void createIndex() throws SQLException {
        TablePreset.create("indextest").putColumn("col", ColumnType.TEXT.struct()).create(getDb());
        assertDoesNotThrow(() -> getDb().createIndex("indextest", TableIndex.index("fulltexttest", "col", TableIndex.Type.INDEX)));
        getDb().drop("indextest");
    }

    @Test
    void createTableWithIndices() throws SQLException {
        Database db = getDb();
        assertDoesNotThrow(() -> TablePreset.create("indicestest")
                .putColumn("col1", ColumnType.TEXT.struct())
                .putColumn("col2", ColumnType.TEXT.struct())
                .addIndex(TableIndex.index("col1index", "col1", TableIndex.Type.FULLTEXT))
                .addIndex(TableIndex.index("col2index", "col2", TableIndex.Type.INDEX))
                .create(db)); // We're testing if TablePreset#create(Database) throws an error here, not if #getDb() does.
        db.drop("indicestest");
    }

    @Test
    void testSelectJoin() throws SQLException {
        SelectResults res = getDb().selectBuilder("join_test_1")
                .select("*")
                .join(Join.builder()
                        .type(JoinType.INNER)
                        .table("join_test_2")
                        .using("id"))
                .where(QueryCondition.func(new QueryFunction("1"))) // Just to check if this causes any syntax errors
                .execute();

        assertEquals(1, res.size());
        assertEquals("Value from table 1", res.get(0).getString("value1"));
        assertEquals(testId, res.get(0).getUUID("value2"));
    }
}