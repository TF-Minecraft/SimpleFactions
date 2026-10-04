package net.tfminecraft.simplefactions.guild;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.objects.Faction;

class RetiredBranchWarningTest {
    @Test
    void retiredBranchesNameTheFactionGuildBranchAndLevel() {
        Logger logger = Logger.getLogger("SimpleFactions");
        List<String> warnings = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue() && record.getMessage() != null) {
                    warnings.add(record.getMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(handler);
        Level previous = logger.getLevel();
        boolean previousUseParent = logger.getUseParentHandlers();
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.WARNING);
        try {
            Faction host = mock(Faction.class);
            when(host.getName()).thenReturn("Vardera");
            Guild.warnRetiredBranch(host, "The Chisels", "supply_lines", 4);
            Guild.warnRetiredBranch(host, "The Realm", "infrastructure", 2);
            Guild.warnRetiredBranch(host, "The Chisels", "storehouses", 3);
            Guild.warnRetiredBranch(host, "The Chisels", "supply_lines", 0);

            assertEquals(2, warnings.size());
            assertTrue(warnings.get(0).contains("Vardera"));
            assertTrue(warnings.get(0).contains("The Chisels"));
            assertTrue(warnings.get(0).contains("supply_lines"));
            assertTrue(warnings.get(0).contains("level 4"));
            assertTrue(warnings.get(0).contains("guild bank grant"));
            assertTrue(warnings.get(1).contains("Vardera"));
            assertTrue(warnings.get(1).contains("The Realm"));
            assertTrue(warnings.get(1).contains("infrastructure"));
            assertTrue(warnings.get(1).contains("level 2"));
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previous);
            logger.setUseParentHandlers(previousUseParent);
        }
    }
}
