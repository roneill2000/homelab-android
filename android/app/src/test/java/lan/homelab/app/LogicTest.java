package lan.homelab.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

// Pure logic only (no Android framework): alert identity/routing and the snooze countdown text.
public class LogicTest {
    @Test public void alertKeysIgnoreCountsAndLogErrors() {
        assertEquals("services-down", Alerts.key("2 services down"));
        assertEquals("services-down", Alerts.key("1 service down"));
        assertEquals("dns", Alerts.key("DNS blocking paused"));
        assertEquals("drive", Alerts.key("Drive health warning"));
        assertEquals("cloud", Alerts.key("Cloud backup stale"));
        assertEquals("backup-job", Alerts.key("Backup job failed"));
        assertEquals("stale", Alerts.key("No fresh readings"));
        assertNull(Alerts.key("3 log errors"));
    }

    @Test public void alertsOpenTheRightPage() {
        assertEquals("services", Alerts.page("services-down"));
        assertEquals("network", Alerts.page("dns"));
        assertEquals("storage", Alerts.page("drive"));
        assertEquals("infra", Alerts.page("cloud"));
        assertEquals("overview", Alerts.page("something-else"));
    }

    @Test public void snoozeTextRoundsUpAndNeverSaysZero() {
        assertEquals("1 min left", SnoozeAlertsTile.leftText(1));
        assertEquals("25 min left", SnoozeAlertsTile.leftText(24 * 60 + 1));
        assertEquals("1 h left", SnoozeAlertsTile.leftText(3600));
        assertEquals("1 h 40 min left", SnoozeAlertsTile.leftText(100 * 60));
        assertEquals("2 h left", SnoozeAlertsTile.leftText(7200));
    }

    @Test public void friendlyModelNames() {
        assertEquals("Strata · Swift 1.5 IQ3_XXS", ModelNames.label("swift-1.5-iq3_xxs"));
        assertEquals("Qwen3.8 27B", ModelNames.label("qwen3.8-27b"));
        assertEquals("Gemma 4 26B MoE", ModelNames.label("gemma-4-26b-a4b"));
        assertEquals("some-new-model", ModelNames.label("some-new-model"));
        assertEquals("", ModelNames.label(null));
        assertEquals(true, ModelNames.isStrata("swift-1.5-iq3_xxs"));
        assertEquals(false, ModelNames.isStrata("qwen3.8-27b"));
    }

    @Test public void answerPreviewAndIds() {
        assertEquals("one two three", Answers.preview("  one\n\ntwo   three "));
        String longText = new String(new char[300]).replace('\0', 'a');
        assertEquals(158, Answers.preview(longText).length());
        assertEquals(true, Answers.validId("3f9c0d2e4b5a4c6d8e7f"));
        assertEquals(false, Answers.validId("bad id'); alert(1)"));
        assertEquals(false, Answers.validId(""));
    }
}
