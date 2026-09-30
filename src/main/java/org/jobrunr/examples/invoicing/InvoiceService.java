package org.jobrunr.examples.invoicing;

import org.jobrunr.jobs.JobId;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.context.JobContext;
import org.jobrunr.scheduling.JobScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * The invoice job: calculate the usage, generate the PDF, charge the card, send the email.
 * <p>
 * Every step is wrapped in {@link JobContext#runStepOnce}, so a retry resumes at the first step
 * that did not complete instead of starting over. The result of a step is stored with the job, so
 * {@code chargeId} in the email step is the id of the charge that actually happened, on whichever
 * attempt it happened.
 */
@Service
public class InvoiceService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final JobScheduler jobScheduler;
    private final PaymentProvider paymentProvider;

    // Only here so the test can assert that a completed step does not run a second time.
    private final AtomicInteger usageCalculations = new AtomicInteger();
    private final AtomicInteger pdfsGenerated = new AtomicInteger();
    private final AtomicInteger emailsSent = new AtomicInteger();

    public InvoiceService(JobScheduler jobScheduler, PaymentProvider paymentProvider) {
        this.jobScheduler = jobScheduler;
        this.paymentProvider = paymentProvider;
    }

    /** Enqueues one durable invoice job per customer. */
    public void startInvoiceRun(String period, int customers) {
        jobScheduler.enqueue(
                IntStream.rangeClosed(1, customers).mapToObj(i -> "CUST-%04d".formatted(i)),
                customerId -> generateInvoice(customerId, period, JobContext.Null));
    }

    public JobId generateSingleInvoice(String customerId, String period) {
        return jobScheduler.enqueue(() -> generateInvoice(customerId, period, JobContext.Null));
    }

    @Job(name = "Invoice %1 for %0", retries = 5)
    public void generateInvoice(String customerId, String period, JobContext jobContext) throws Exception {
        String invoiceId = "INV-" + period + "-" + customerId;

        long usage = jobContext.runStepOnce("calculate-usage", () -> calculateUsage(customerId, period));
        String pdf = jobContext.runStepOnce("generate-pdf", () -> generatePdf(invoiceId, usage));
        String chargeId = jobContext.runStepOnce("charge-card", () -> paymentProvider.charge(customerId, invoiceId));
        jobContext.runStepOnce("send-email", () -> sendInvoiceEmail(customerId, pdf, chargeId));
    }

    private long calculateUsage(String customerId, String period) throws InterruptedException {
        usageCalculations.incrementAndGet();
        work(200, 500);
        return ThreadLocalRandom.current().nextLong(1_000, 250_000); // metered job executions
    }

    private String generatePdf(String invoiceId, long usage) throws InterruptedException {
        pdfsGenerated.incrementAndGet();
        work(700, 1400);
        return "s3://invoices/" + invoiceId + ".pdf";
    }

    private void sendInvoiceEmail(String customerId, String pdf, String chargeId) throws InterruptedException {
        emailsSent.incrementAndGet();
        work(150, 400);
        logger.info("Mailed {} to {} (charge {})", pdf, customerId, chargeId);
    }

    private static void work(long minMillis, long maxMillis) throws InterruptedException {
        Thread.sleep(ThreadLocalRandom.current().nextLong(minMillis, maxMillis));
    }

    public int usageCalculations() {
        return usageCalculations.get();
    }

    public int pdfsGenerated() {
        return pdfsGenerated.get();
    }

    public int emailsSent() {
        return emailsSent.get();
    }
}
