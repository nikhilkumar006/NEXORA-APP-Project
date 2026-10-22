package com.nexora.xp;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class XpRewardRulesTest {
    @Test
    void contributionRedemptionRequiresFiftyXpForTwentyFiveRupees() {
        assertEquals(50, XpService.REWARD_THRESHOLD_XP);
        assertEquals(25, XpService.REWARD_AMOUNT_RUPEES);
    }
}
