package org.de013.shoppingcart.config;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

@Component
public class CouponMigrationGuard implements Callback {
    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.BEFORE_MIGRATE;
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return true;
    }

    @Override
    public void handle(Event event, Context context) {
        try {
            var connection = context.getConnection();
            String schema = context.getConfiguration().getDefaultSchema();
            if (schema == null) {
                String[] schemas = context.getConfiguration().getSchemas();
                schema = schemas.length == 0 ? connection.getSchema() : schemas[0];
            }
            try (var check = connection.prepareStatement("select exists (select 1 from pg_catalog.pg_attribute a join pg_catalog.pg_class c on c.oid=a.attrelid join pg_catalog.pg_namespace n on n.oid=c.relnamespace where n.nspname=? and c.relname='carts' and c.relkind in ('r','p') and a.attname='coupon_code' and a.attnum>0 and not a.attisdropped)")) {
                check.setString(1, schema);
                try (var result = check.executeQuery()) {
                    result.next();
                    if (!result.getBoolean(1)) return;
                }
            }
            String quotedSchema = connection.getMetaData().getIdentifierQuoteString() + schema.replace("\"", "\"\"") + "\"";
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("select exists (select 1 from " + quotedSchema + ".carts where coupon_code is not null)")) {
                result.next();
                if (result.getBoolean(1)) throw new FlywayException("Cart migration refused: non-null coupon_code exists; preserve data and stop.");
            }
        } catch (SQLException e) {
            throw new FlywayException("Unable to verify cart coupon data before migration", e);
        }
    }

    @Override
    public String getCallbackName() {
        return "cart-coupon-migration-guard";
    }
}
