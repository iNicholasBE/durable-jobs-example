package org.jobrunr.examples.invoicing;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final PaymentProvider paymentProvider;

    public InvoiceController(InvoiceService invoiceService, PaymentProvider paymentProvider) {
        this.invoiceService = invoiceService;
        this.paymentProvider = paymentProvider;
    }

    @PostMapping("/run")
    public String startInvoiceRun(@RequestParam(defaultValue = "2026-09") String period,
                                  @RequestParam(defaultValue = "250") int customers) {
        invoiceService.startInvoiceRun(period, customers);
        return "Enqueued " + customers + " invoice jobs for " + period;
    }

    @PostMapping("/single")
    public String generateSingleInvoice(@RequestParam(defaultValue = "CUST-0042") String customerId,
                                        @RequestParam(defaultValue = "2026-09") String period) {
        return invoiceService.generateSingleInvoice(customerId, period).toString();
    }

    @PostMapping("/payment-provider/outage")
    public String outage(@RequestParam boolean enabled) {
        paymentProvider.setOutage(enabled);
        return "Payment provider outage: " + enabled;
    }
}
