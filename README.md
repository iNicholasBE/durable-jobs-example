# Durable jobs example

The demo project behind the article *Durable Execution You Can Watch, Without a Workflow Engine*.

A Spring Boot application with one job that matters: an invoice job with four steps (calculate the
usage, generate the PDF, charge the card, send the email), a payment provider you can break with a
switch, and the JobRunr dashboard on port 8000. Everything here runs on JobRunr open source.

## The job

Every step is wrapped in `runStepOnce`, so a retry resumes at the first step that did not complete
instead of starting over. The result of a completed step is stored with the job, which is why
`chargeId` in the email step is always the id of the charge that actually happened, on whichever
attempt it happened.

```java
@Job(name = "Invoice %1 for %0", retries = 5)
public void generateInvoice(String customerId, String period, JobContext jobContext) throws Exception {
    String invoiceId = "INV-" + period + "-" + customerId;

    long usage = jobContext.runStepOnce("calculate-usage", () -> calculateUsage(customerId, period));
    String pdf = jobContext.runStepOnce("generate-pdf", () -> generatePdf(invoiceId, usage));
    String chargeId = jobContext.runStepOnce("charge-card", () -> paymentProvider.charge(customerId, invoiceId));
    jobContext.runStepOnce("send-email", () -> sendInvoiceEmail(customerId, pdf, chargeId));
}
```

See [InvoiceService.java](src/main/java/org/jobrunr/examples/invoicing/InvoiceService.java).

## Run it

```bash
mvn spring-boot:run
```

The app listens on 8080 and the JobRunr dashboard on <http://localhost:8000/dashboard>. The default
storage is H2 in memory, so nothing survives a restart. To run against Postgres instead, start the
container in the comment at the top of
[application-postgres.properties](src/main/resources/application-postgres.properties) and add
`-Dspring-boot.run.profiles=postgres`.

## Replay the incident

`scenario.sh` drives the app over HTTP.

```bash
./scenario.sh phase2
```

That flips the payment provider into an outage, enqueues one invoice for `CUST-0042`, waits for the
first attempt to fail on `charge-card`, brings the provider back, and waits for the retry to
succeed. It prints the job id at the end: open it in the dashboard and switch its History from
`Timeline` to `Chart`. Two hollow diamonds sit where `calculate-usage` and `generate-pdf` would run,
because they completed on the first attempt.

`./scenario.sh phase1` runs a healthy batch of 150 invoices instead, which is useful if you want a
dashboard with some history in it.

## The endpoints

| Endpoint | What it does |
|---|---|
| `POST /invoices/single?customerId=CUST-0042&period=2026-09` | Enqueues one durable invoice job and returns its id |
| `POST /invoices/run?period=2026-09&customers=250` | Enqueues one durable invoice job per customer |
| `POST /invoices/payment-provider/outage?enabled=true` | Makes every card charge time out after 2 seconds |

## The test

[DurableInvoiceJobTest](src/test/java/org/jobrunr/examples/invoicing/DurableInvoiceJobTest.java) is
the article's claim written as an assertion: after a failed charge and a retry, the usage was
calculated once, the PDF was generated once, the email was sent once, and the customer was charged
once.

```bash
mvn test
```

## Notes

- The default poll interval is 15 seconds, so a freshly enqueued job can sit in `ENQUEUED` for a few
  seconds before a worker picks it up. That is the long blue bar in the article's screenshot, not a
  slow job.
- `runStepOnce` guarantees that a *completed* step does not run again. A step that was still running
  when the JVM died has completed nothing and will run again on the retry, so a side effect inside a
  step still wants an idempotency key. This demo passes the invoice id to the payment provider as
  exactly that, and the provider deduplicates on it.
- Code between the steps runs on every attempt. Building `invoiceId` is a pure computation so that is
  harmless, but anything with a side effect belongs inside a step.

The [durable executions guide](https://www.jobrunr.io/en/guides/advanced/durable-executions/) covers
the API in more depth.
