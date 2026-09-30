package org.jobrunr.examples.invoicing;

import org.jobrunr.jobs.JobId;
import org.jobrunr.jobs.states.StateName;
import org.jobrunr.storage.StorageProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The claim the articles make, as a test: when the card charge fails, the retry resumes at
 * charge-card and the two steps that already completed do not run again.
 */
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.poll-interval-in-seconds=5")
class DurableInvoiceJobTest {

    @Autowired
    InvoiceService invoiceService;

    @Autowired
    PaymentProvider paymentProvider;

    @Autowired
    StorageProvider storageProvider;

    @Test
    void aRetryResumesAtTheStepThatFailed() {
        paymentProvider.setOutage(true);

        JobId jobId = invoiceService.generateSingleInvoice("CUST-0042", "2026-09");

        // attempt 1 gets through calculate-usage and generate-pdf, then dies on charge-card
        await().atMost(Duration.ofSeconds(60))
                .until(() -> storageProvider.getJobById(jobId).hasState(StateName.SCHEDULED));
        assertThat(invoiceService.usageCalculations()).isEqualTo(1);
        assertThat(invoiceService.pdfsGenerated()).isEqualTo(1);
        assertThat(invoiceService.emailsSent()).isZero();

        paymentProvider.setOutage(false);

        await().atMost(Duration.ofSeconds(120))
                .until(() -> storageProvider.getJobById(jobId).hasState(StateName.SUCCEEDED));

        // the retry skipped the two completed steps and only did what was left
        assertThat(invoiceService.usageCalculations()).isEqualTo(1);
        assertThat(invoiceService.pdfsGenerated()).isEqualTo(1);
        assertThat(invoiceService.emailsSent()).isEqualTo(1);

        // and the customer was charged exactly once
        assertThat(paymentProvider.chargeCount()).isEqualTo(1);
    }
}
