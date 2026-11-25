package com.gediadeepak.smsapp.core
import org.junit.Assert.*
import org.junit.Test

class CloudBackupPolicyTest {
    @Test fun automaticPhonesHaveDistinctSourcesAndKeepTheManualCopySeparate() {
        val a=CloudBackupPolicy.automaticSource("00000000-0000-4000-8000-000000000001")
        val b=CloudBackupPolicy.automaticSource("00000000-0000-4000-8000-000000000002")
        assertNotEquals(a,b);assertNotEquals("latest",a)
        assertTrue(CloudBackupPolicy.validSource(a));assertTrue(CloudBackupPolicy.validSource("latest"))
    }
    @Test fun rejectsForeignPathsAndMalformedInstallationIds() {
        for(id in listOf("","auto-latest","../latest","auto-../../other","accounts/another")) assertFalse(CloudBackupPolicy.validSource(id))
        try {CloudBackupPolicy.automaticSource("latest");fail()} catch(expected:IllegalArgumentException) {}
    }
}
