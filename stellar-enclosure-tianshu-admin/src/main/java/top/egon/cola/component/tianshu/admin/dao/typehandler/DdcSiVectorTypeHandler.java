package top.egon.cola.component.tianshu.admin.dao.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * 纯 JDBC 的 pgvector {@code vector} 双向类型处理器：仅借助标准 JDBC {@link Types#OTHER} 与无空格
 * {@code [v1,v2,...]} 文本字面量完成 {@code float[]} 转换，不引入第三方驱动或非 BOM 依赖。
 */
public class DdcSiVectorTypeHandler extends BaseTypeHandler<float[]> {

    private static final String EMPTY_VECTOR = "EMBEDDING_VECTOR_EMPTY";
    private static final String NON_FINITE_VECTOR = "EMBEDDING_VECTOR_NOT_FINITE";
    private static final String MALFORMED_LITERAL = "EMBEDDING_VECTOR_LITERAL_MALFORMED";

    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, float[] parameter,
                                    JdbcType jdbcType) throws SQLException {
        statement.setObject(index, toLiteral(parameter), Types.OTHER);
    }

    @Override
    public float[] getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return fromLiteral(resultSet.getString(columnName));
    }

    @Override
    public float[] getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return fromLiteral(resultSet.getString(columnIndex));
    }

    @Override
    public float[] getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return fromLiteral(statement.getString(columnIndex));
    }

    /** 将非空且全维有限的向量格式化为 pgvector {@code [v1,v2,...]} 字面量。 */
    public static String toLiteral(float[] vector) {
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException(EMPTY_VECTOR);
        }
        StringBuilder literal = new StringBuilder(vector.length * 8 + 2);
        literal.append('[');
        for (int index = 0; index < vector.length; index++) {
            float component = vector[index];
            if (!Float.isFinite(component)) {
                throw new IllegalArgumentException(NON_FINITE_VECTOR);
            }
            if (index > 0) {
                literal.append(',');
            }
            literal.append(component);
        }
        return literal.append(']').toString();
    }

    private static float[] fromLiteral(String text) throws SQLException {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.length() < 2 || trimmed.charAt(0) != '['
                || trimmed.charAt(trimmed.length() - 1) != ']') {
            throw new SQLException(MALFORMED_LITERAL);
        }
        String body = trimmed.substring(1, trimmed.length() - 1).trim();
        if (body.isEmpty()) {
            throw new IllegalArgumentException(EMPTY_VECTOR);
        }
        String[] tokens = body.split(",", -1);
        float[] vector = new float[tokens.length];
        for (int index = 0; index < tokens.length; index++) {
            float component;
            try {
                component = Float.parseFloat(tokens[index].trim());
            } catch (NumberFormatException failure) {
                throw new SQLException(MALFORMED_LITERAL, failure);
            }
            if (!Float.isFinite(component)) {
                throw new SQLException(NON_FINITE_VECTOR);
            }
            vector[index] = component;
        }
        return vector;
    }
}
