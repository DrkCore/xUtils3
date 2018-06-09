package core.xmate.db.table;

import android.database.Cursor;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import core.xmate.db.DbException;
import core.xmate.db.DbManager;
import core.xmate.db.sqlite.SqlInfo;
import core.xmate.db.sqlite.SqlInfoBuilder;
import core.xmate.util.IOUtil;
import core.xmate.util.LogUtil;

/**
 * DbManager基类, 包含表结构的基本操作.
 * Created by wyouflf on 16/1/22.
 */
public abstract class DbBase implements DbManager {

    private final HashMap<Class<?>, TableEntity<?>> tableMap = new HashMap<>();
    private final HashMap<Class<?>, ModelEntity<?>> modelsMap = new HashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> TableEntity<T> getTable(Class<T> entityType) throws DbException {
        synchronized (tableMap) {
            TableEntity<T> table = (TableEntity<T>) tableMap.get(entityType);
            if (table == null) {
                try {
                    table = new TableEntity<T>(this, entityType);
                } catch (Throwable ex) {
                    throw new DbException(ex);
                }
                tableMap.put(entityType, table);
            }

            return table;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> ModelEntity<T> getModel(Class<T> entityType) throws DbException {
        synchronized (modelsMap) {
            ModelEntity<T> modelEntity = (ModelEntity<T>) modelsMap.get(entityType);
            if (modelEntity == null) {
                try {
                    modelEntity = new ModelEntity<>(entityType);
                } catch (Throwable ex) {
                    throw new DbException(ex);
                }
                modelsMap.put(entityType, modelEntity);
            }
            return modelEntity;
        }
    }

    @Override
    public void dropTable(Class<?> entityType) throws DbException {
        TableEntity<?> table = this.getTable(entityType);
        if (!table.tableIsExist()) return;
        execNonQuery("DROP TABLE \"" + table.getName() + "\"");
        table.setCheckedDatabase(false);
        this.removeTable(entityType);
    }

    @Override
    public void dropTableQuietly(Class<?> entityType) {
        try {
            dropTable(entityType);
        } catch (DbException e) {
            LogUtil.e("dropTableQuietly", e);
        }
    }

    @Override
    public void dropDb() throws DbException {
        Cursor cursor = execQuery("SELECT name FROM sqlite_master WHERE type='table' AND name<>'sqlite_sequence'");
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    try {
                        String tableName = cursor.getString(0);
                        execNonQuery("DROP TABLE " + tableName);
                    } catch (Throwable e) {
                        LogUtil.e(e.getMessage(), e);
                    }
                }

                synchronized (tableMap) {
                    for (TableEntity<?> table : tableMap.values()) {
                        table.setCheckedDatabase(false);
                    }
                    tableMap.clear();
                }
            } catch (Throwable e) {
                throw new DbException(e);
            } finally {
                IOUtil.closeQuietly(cursor);
            }
        }
    }

    @Override
    public void dropDbQuietly() {
        try {
            dropDb();
        } catch (DbException e) {
            LogUtil.e(e.getMessage(), e);
        }
    }

    @Override
    public List<String> getTableNames() throws DbException {
        List<String> tables = new ArrayList<>();
        Cursor cursor = execQuery("SELECT name FROM sqlite_master WHERE type='table' AND name<>'sqlite_sequence'");
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    try {
                        tables.add(cursor.getString(0));
                    } catch (Throwable e) {
                        LogUtil.e(e.getMessage(), e);
                    }
                }
            } catch (Throwable e) {
                throw new DbException(e);
            } finally {
                IOUtil.closeQuietly(cursor);
            }
        }

        return tables;
    }

    @Override
    public boolean isTableExists(Class<?> tableEntity) throws DbException {
        TableEntity<?> table = getTable(tableEntity);
        return isTableExists(table.getName());
    }

    @Override
    public boolean isTableExists(String name) throws DbException {
        boolean result = false;

        SqlInfo sqlInfo = new SqlInfo("SELECT name FROM sqlite_master WHERE type='table' AND name=?").addBindArgs(name);
        Cursor cursor = execQuery(sqlInfo);
        if (cursor != null) {
            try {
                result = cursor.moveToNext();
            } catch (Throwable e) {
                throw new DbException(e);
            } finally {
                IOUtil.closeQuietly(cursor);
            }
        }

        return result;
    }

    @Override
    public void addColumn(Class<?> entityType, String column) throws DbException {
        TableEntity<?> table = this.getTable(entityType);
        ColumnEntity col = table.getColumnMap().get(column);
        if (col != null) {
            StringBuilder builder = new StringBuilder();
            builder.append("ALTER TABLE ").append("\"").append(table.getName()).append("\"").
                    append(" ADD COLUMN ").append("\"").append(col.getName()).append("\"").
                    append(" ").append(col.getColumnDbType()).
                    append(" ").append(col.getProperty());
            execNonQuery(builder.toString());
        }
    }

    @Override
    public void createTableIfNotExist(Class<?> entityType) throws DbException {
        TableEntity<?> tableEntity = getTable(entityType);
        createTableIfNotExist(tableEntity);
    }

    protected void createTableIfNotExist(TableEntity<?> table) throws DbException {
        if (!table.tableIsExist()) {
            synchronized (table.getClass()) {
                if (!table.tableIsExist()) {
                    SqlInfo sqlInfo = SqlInfoBuilder.buildCreateTableSqlInfo(table);
                    execNonQuery(sqlInfo);
                    String[] execAfterTableCreated = table.getOnCreated();
                    for (String sql : execAfterTableCreated) {
                        if (!TextUtils.isEmpty(sql)) {
                            execNonQuery(sql);
                        }
                    }
                    table.setCheckedDatabase(true);
                    TableCreateListener listener = this.getDaoConfig().getTableCreateListener();
                    if (listener != null) {
                        listener.onTableCreated(this, table);
                    }
                }
            }
        }
    }

    protected void removeTable(Class<?> entityType) {
        synchronized (tableMap) {
            tableMap.remove(entityType);
        }
    }

}