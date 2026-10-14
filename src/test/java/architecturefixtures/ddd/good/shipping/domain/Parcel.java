package architecturefixtures.ddd.good.shipping.domain;

// Another feature keeps a scalar order identity and may use shared root domain values.
public record Parcel(java.util.UUID orderId, architecturefixtures.ddd.good.domain.Money declaredValue) {}
