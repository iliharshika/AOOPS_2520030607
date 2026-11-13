import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

// Save this file as MoneyPricingDemo.java
public class MoneyPricingDemo {

    // A fixed set of supported currencies and their minor-unit precision.
    enum Currency {
        USD(2), EUR(2), JPY(0), KWD(3);

        private final int minorUnits;

        Currency(int minorUnits) {
            this.minorUnits = minorUnits;
        }
    }

    // Value object: money is stored as an integer number of minor units.
    static final class Money {
        private final long minor;
        private final Currency currency;

        private Money(long minor, Currency currency) {
            if (minor < 0) {
                throw new IllegalArgumentException("Money amount cannot be negative.");
            }
            this.minor = minor;
            this.currency = Objects.requireNonNull(currency);
        }

        public static Money of(String amount, Currency currency) {
            Objects.requireNonNull(amount);
            Objects.requireNonNull(currency);

            BigDecimal value = new BigDecimal(amount);
            if (value.signum() < 0) {
                throw new IllegalArgumentException("Money amount cannot be negative.");
            }

            // Rejects amounts with more decimal places than the currency permits.
            long minorUnits = value
                    .setScale(currency.minorUnits, RoundingMode.UNNECESSARY)
                    .movePointRight(currency.minorUnits)
                    .longValueExact();

            return new Money(minorUnits, currency);
        }

        public Money plus(Money other) {
            requireSameCurrency(other);
            return new Money(Math.addExact(minor, other.minor), currency);
        }

        // Returns the amount remaining after applying a discount percentage.
        public Money minus(Percentage discount) {
            Objects.requireNonNull(discount);

            BigDecimal remaining = BigDecimal.valueOf(minor)
                    .multiply(BigDecimal.valueOf(10000L - discount.basisPoints))
                    .divide(BigDecimal.valueOf(10000), 0, RoundingMode.HALF_EVEN);

            return new Money(remaining.longValueExact(), currency);
        }

        public Money times(Quantity quantity) {
            Objects.requireNonNull(quantity);
            return new Money(Math.multiplyExact(minor, quantity.value), currency);
        }

        private void requireSameCurrency(Money other) {
            Objects.requireNonNull(other);
            if (currency != other.currency) {
                throw new IllegalArgumentException(
                        "Currency mismatch: " + currency + " and " + other.currency);
            }
        }

        public Currency getCurrency() {
            return currency;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Money)) return false;
            Money other = (Money) obj;
            return minor == other.minor && currency == other.currency;
        }

        @Override
        public int hashCode() {
            return Objects.hash(minor, currency);
        }

        @Override
        public String toString() {
            return BigDecimal.valueOf(minor, currency.minorUnits).toPlainString()
                    + " " + currency;
        }
    }

    // Value object: 10,000 basis points represent 100 percent.
    static final class Percentage {
        private final int basisPoints;

        private Percentage(int basisPoints) {
            if (basisPoints < 0 || basisPoints > 10000) {
                throw new IllegalArgumentException(
                        "Discount must be between 0 and 100 percent.");
            }
            this.basisPoints = basisPoints;
        }

        public static Percentage of(int wholePercent) {
            if (wholePercent < 0 || wholePercent > 100) {
                throw new IllegalArgumentException("Invalid percentage.");
            }
            return new Percentage(wholePercent * 100);
        }

        public static Percentage ofBasisPoints(int basisPoints) {
            return new Percentage(basisPoints);
        }

        // Calculates the discount amount using HALF_EVEN rounding.
        public Money applyTo(Money money) {
            Objects.requireNonNull(money);

            BigDecimal result = BigDecimal.valueOf(money.minor)
                    .multiply(BigDecimal.valueOf(basisPoints))
                    .divide(BigDecimal.valueOf(10000), 0, RoundingMode.HALF_EVEN);

            return new Money(result.longValueExact(), money.currency);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Percentage)) return false;
            Percentage other = (Percentage) obj;
            return basisPoints == other.basisPoints;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(basisPoints);
        }

        @Override
        public String toString() {
            return BigDecimal.valueOf(basisPoints, 2).toPlainString() + "%";
        }
    }

    // Value object: quantity must be a positive whole number.
    static final class Quantity {
        private final int value;

        private Quantity(int value) {
            if (value < 1) {
                throw new IllegalArgumentException("Quantity must be at least 1.");
            }
            this.value = value;
        }

        public static Quantity of(int value) {
            return new Quantity(value);
        }

        public int getValue() {
            return value;
        }
    }

    // Entity: a product is identified by its SKU.
    static final class Product {
        private final String sku;
        private final String name;
        private final Money price;
        private final Percentage discount;

        public Product(String sku, String name, Money price, Percentage discount) {
            if (sku == null || sku.trim().isEmpty()) {
                throw new IllegalArgumentException("SKU cannot be empty.");
            }
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("Product name cannot be empty.");
            }
            this.sku = sku;
            this.name = name;
            this.price = Objects.requireNonNull(price);
            this.discount = Objects.requireNonNull(discount);
        }

        public String getSku() {
            return sku;
        }

        public String getName() {
            return name;
        }

        public Money getDiscountedPrice() {
            return price.minus(discount);
        }
    }

    // A cart line combines a product and a positive quantity.
    static final class CartLine {
        private final Product product;
        private int quantity;

        private CartLine(Product product, int quantity) {
            this.product = product;
            this.quantity = quantity;
        }

        private void increaseQuantity(int amount) {
            if (amount < 1) {
                throw new IllegalArgumentException("Quantity must be positive.");
            }
            quantity = Math.addExact(quantity, amount);
        }

        public Money lineTotal() {
            return product.getDiscountedPrice().times(Quantity.of(quantity));
        }

        public Product getProduct() {
            return product;
        }

        public int getQuantity() {
            return quantity;
        }
    }

    // Entity / aggregate root: cart is identified by its cart ID.
    static final class Cart {
        private final String cartId;
        private final Currency currency;
        private final Map<String, CartLine> lines = new LinkedHashMap<>();

        public Cart(String cartId, Currency currency) {
            if (cartId == null || cartId.trim().isEmpty()) {
                throw new IllegalArgumentException("Cart ID cannot be empty.");
            }
            this.cartId = cartId;
            this.currency = Objects.requireNonNull(currency);
        }

        public void addProduct(Product product, Quantity quantity) {
            Objects.requireNonNull(product);
            Objects.requireNonNull(quantity);

            if (product.getDiscountedPrice().getCurrency() != currency) {
                throw new IllegalArgumentException(
                        "Product currency does not match cart currency.");
            }

            CartLine existing = lines.get(product.getSku());
            if (existing == null) {
                lines.put(product.getSku(),
                        new CartLine(product, quantity.getValue()));
            } else {
                existing.increaseQuantity(quantity.getValue());
            }
        }

        public Money total() {
            Money total = Money.of("0", currency);
            for (CartLine line : lines.values()) {
                total = total.plus(line.lineTotal());
            }
            return total;
        }

        public void displayCart() {
            System.out.println("\nCart ID: " + cartId);
            System.out.println("----------------------------------------");
            for (CartLine line : lines.values()) {
                System.out.println(line.getProduct().getName()
                        + " | Quantity: " + line.getQuantity()
                        + " | Unit price after discount: "
                        + line.getProduct().getDiscountedPrice()
                        + " | Line total: " + line.lineTotal());
            }
            System.out.println("----------------------------------------");
            System.out.println("Cart Total: " + total());
        }
    }

    public static void main(String[] args) {
        try {
            Product laptop = new Product(
                    "P101",
                    "Laptop",
                    Money.of("1000.00", Currency.USD),
                    Percentage.of(10)
            );

            Product mouse = new Product(
                    "P102",
                    "Wireless Mouse",
                    Money.of("25.50", Currency.USD),
                    Percentage.of(5)
            );

            Cart cart = new Cart("CART-1001", Currency.USD);

            cart.addProduct(laptop, Quantity.of(1));
            cart.addProduct(mouse, Quantity.of(2));

            // Adding the same SKU again increases its quantity.
            cart.addProduct(mouse, Quantity.of(1));

            cart.displayCart();

            Money price = Money.of("100.00", Currency.USD);
            Percentage discount = Percentage.of(10);

            System.out.println("\nPercentage Demonstration");
            System.out.println("Original Price: " + price);
            System.out.println("Discount: " + discount);
            System.out.println("Discount Amount: " + discount.applyTo(price));
            System.out.println("Price After Discount: " + price.minus(discount));

            // Demonstrates that different currencies cannot be added.
            System.out.println("\nCurrency Safety Demonstration");
            Money dollars = Money.of("10.00", Currency.USD);
            Money euros = Money.of("10.00", Currency.EUR);

            try {
                System.out.println(dollars.plus(euros));
            } catch (IllegalArgumentException ex) {
                System.out.println("Correctly rejected: " + ex.getMessage());
            }

            // Demonstrates invalid discount validation.
            System.out.println("\nValidation Demonstration");
            try {
                Percentage.of(150);
            } catch (IllegalArgumentException ex) {
                System.out.println("Correctly rejected: " + ex.getMessage());
            }

        } catch (ArithmeticException ex) {
            System.out.println("Arithmetic overflow: " + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            System.out.println("Invalid input: " + ex.getMessage());
        }
    }
}
