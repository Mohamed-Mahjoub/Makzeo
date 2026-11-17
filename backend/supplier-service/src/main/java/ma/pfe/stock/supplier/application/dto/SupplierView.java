package ma.pfe.stock.supplier.application.dto;

import ma.pfe.stock.supplier.domain.model.Supplier;

import java.time.Instant;

public record SupplierView(Long id, String code, String name, String contactName, String email, String phone,
                           String address, String city, String country, String countryName, String taxId, String notes,
                           boolean active,
                           Instant createdAt, Instant updatedAt) {

    public static SupplierView from(Supplier s) {
        return new SupplierView(s.getId(), s.getCode(), s.getName(), s.getContactName(), s.getEmail(),
                s.getPhone(), s.getAddress(), s.getCity(), s.getCountry(), s.getCountryName(), s.getTaxId(), s.getNotes(), s.isActive(),
                s.getCreatedAt(), s.getUpdatedAt());
    }
}
