package ma.pfe.stock.supplier.application.dto;

public record SupplierCommand(String code, String name, String contactName, String email, String phone,
                              String address, String city, String country, String taxId, String notes) {
}
