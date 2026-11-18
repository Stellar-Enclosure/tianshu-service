package top.egon.cola.component.tianshu.admin.dao.typehandler;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/** Explicit native PostgreSQL JSONB object mapping; SQL NULL remains missing historical metadata. */
public final class DdcJsonbTypeHandler extends BaseTypeHandler<JsonNode> {

    private static final ObjectMapper MAPPER = new ObjectMapper(new JsonFactory()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION))
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, JsonNode value, JdbcType jdbcType)
            throws SQLException {
        if (!value.isObject()) { throw new SQLException("TIANSHU_MANIFEST_JSON_OBJECT_REQUIRED"); }
        try {
            PGobject jsonb = new PGobject();
            jsonb.setType("jsonb");
            jsonb.setValue(MAPPER.writeValueAsString(value));
            statement.setObject(index, jsonb, Types.OTHER);
        } catch (JsonProcessingException failure) {
            throw new SQLException("TIANSHU_MANIFEST_JSON_SERIALIZATION_FAILED", failure);
        }
    }

    @Override
    public JsonNode getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return parse(resultSet.getString(columnName));
    }

    @Override
    public JsonNode getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return parse(resultSet.getString(columnIndex));
    }

    @Override
    public JsonNode getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return parse(statement.getString(columnIndex));
    }

    private static JsonNode parse(String value) throws SQLException {
        if (value == null) { return null; }
        try {
            JsonNode parsed = MAPPER.readTree(value);
            if (parsed == null || !parsed.isObject()) { throw new SQLException("TIANSHU_MANIFEST_JSON_OBJECT_REQUIRED"); }
            return parsed;
        } catch (JsonProcessingException failure) {
            throw new SQLException("TIANSHU_MANIFEST_JSON_DESERIALIZATION_FAILED", failure);
        }
    }
}
