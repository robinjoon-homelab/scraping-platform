package architecturefixtures.ddd.good.shipping.application.service;

public class ShippingService {
    public architecturefixtures.ddd.good.shipping.domain.Parcel pack(architecturefixtures.ddd.good.billing.orders.application.model.OrderSummary order) {
        return new architecturefixtures.ddd.good.shipping.domain.Parcel(order.orderId(), new architecturefixtures.ddd.good.domain.Money(order.totalCents()));
    }
}
