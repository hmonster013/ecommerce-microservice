package org.de013.orderservice.entity.valueobject;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;

/**
 * Address Value Object
 * 
 * Represents a complete address with validation and formatting capabilities.
 * This is an embeddable value object that can be used in various entities.
 * 
 * @author Development Team
 * @version 1.0.0
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode
public class Address implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * First name of the recipient
     */
    @Schema(description = "First name of the recipient", example = "John")
    @Column(name = "first_name", length = 100)
    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must not exceed 100 characters")
    private String firstName;

    /**
     * Last name of the recipient
     */
    @Schema(description = "Last name of the recipient", example = "Doe")
    @Column(name = "last_name", length = 100)
    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String lastName;

    /**
     * Company name (optional)
     */
    @Schema(description = "Company name (optional)", example = "Acme Corp")
    @Column(name = "company", length = 200)
    @Size(max = 200, message = "Company name must not exceed 200 characters")
    private String company;

    /**
     * Street address line 1
     */
    @Schema(description = "Street address line 1", example = "123 Main Street")
    @Column(name = "street_address", length = 255)
    @NotBlank(message = "Street address is required")
    @Size(max = 255, message = "Street address must not exceed 255 characters")
    private String streetAddress;
    
    /**
     * Street address line 2 (optional)
     */
    @Schema(description = "Street address line 2 (optional)", example = "Apt 4B")
    @Column(name = "street_address_2", length = 255)
    @Size(max = 255, message = "Street address line 2 must not exceed 255 characters")
    private String streetAddress2;

    /**
     * City
     */
    @Schema(description = "City name", example = "New York")
    @Column(name = "city", length = 100)
    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City must not exceed 100 characters")
    private String city;

    /**
     * State or province
     */
    @Schema(description = "State or province", example = "NY")
    @Column(name = "state", length = 100)
    @NotBlank(message = "State is required")
    @Size(max = 100, message = "State must not exceed 100 characters")
    private String state;

    /**
     * Postal or ZIP code
     */
    @Schema(description = "Postal or ZIP code", example = "10001")
    @Column(name = "postal_code", length = 20)
    @NotBlank(message = "Postal code is required")
    @Size(max = 20, message = "Postal code must not exceed 20 characters")
    private String postalCode;
    
    /**
     * Country code (ISO 3166-1 alpha-2)
     */
    @Schema(description = "Country code (ISO 3166-1 alpha-2 or alpha-3)", example = "US")
    @Column(name = "country", length = 3)
    @NotBlank(message = "Country is required")
    @Size(min = 2, max = 3, message = "Country code must be 2-3 characters")
    @Pattern(regexp = "^[A-Z]{2,3}$", message = "Country code must be uppercase letters")
    private String country;

    /**
     * Phone number (optional)
     */
    @Schema(description = "Phone number in international format", example = "+15551234567")
    @Column(name = "phone", length = 20)
    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format")
    private String phone;

    /**
     * Email address (optional)
     */
    @Schema(description = "Email address", example = "john.doe@example.com")
    @Column(name = "email", length = 255)
    @Size(max = 255, message = "Email must not exceed 255 characters")
    @Pattern(regexp = "^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$",
             message = "Invalid email format")
    private String email;
    
    /**
     * Special delivery instructions
     */
    @Schema(description = "Special delivery instructions", example = "Leave at front door")
    @Column(name = "delivery_instructions", length = 1000)
    @Size(max = 1000, message = "Delivery instructions must not exceed 1000 characters")
    private String deliveryInstructions;

    /**
     * Address type (HOME, WORK, OTHER)
     */
    @Schema(description = "Address type", example = "HOME")
    @Column(name = "address_type", length = 20)
    @Size(max = 20, message = "Address type must not exceed 20 characters")
    @Builder.Default
    private String addressType = "HOME";

    /**
     * Whether this is a residential address
     */
    @Schema(description = "Whether this is a residential address", example = "true")
    @Column(name = "is_residential")
    @Builder.Default
    private Boolean isResidential = true;
    
    /**
     * Get the full name (first + last name)
     *
     * @return full name
     */
    @JsonIgnore
    public String getFullName() {
        StringBuilder fullName = new StringBuilder();
        
        if (StringUtils.isNotBlank(firstName)) {
            fullName.append(firstName.trim());
        }
        
        if (StringUtils.isNotBlank(lastName)) {
            if (fullName.length() > 0) {
                fullName.append(" ");
            }
            fullName.append(lastName.trim());
        }
        
        return fullName.toString();
    }
    
    /**
     * Get the complete street address (line 1 + line 2)
     *
     * @return complete street address
     */
    @JsonIgnore
    public String getCompleteStreetAddress() {
        StringBuilder address = new StringBuilder();
        
        if (StringUtils.isNotBlank(streetAddress)) {
            address.append(streetAddress.trim());
        }
        
        if (StringUtils.isNotBlank(streetAddress2)) {
            if (address.length() > 0) {
                address.append(", ");
            }
            address.append(streetAddress2.trim());
        }
        
        return address.toString();
    }
    
    /**
     * Get formatted address as a single line
     *
     * @return formatted single-line address
     */
    @JsonIgnore
    public String getFormattedSingleLine() {
        StringBuilder formatted = new StringBuilder();
        
        // Add street address
        String completeStreet = getCompleteStreetAddress();
        if (StringUtils.isNotBlank(completeStreet)) {
            formatted.append(completeStreet);
        }
        
        // Add city
        if (StringUtils.isNotBlank(city)) {
            if (formatted.length() > 0) {
                formatted.append(", ");
            }
            formatted.append(city.trim());
        }
        
        // Add state
        if (StringUtils.isNotBlank(state)) {
            if (formatted.length() > 0) {
                formatted.append(", ");
            }
            formatted.append(state.trim());
        }
        
        // Add postal code
        if (StringUtils.isNotBlank(postalCode)) {
            if (formatted.length() > 0) {
                formatted.append(" ");
            }
            formatted.append(postalCode.trim());
        }
        
        // Add country
        if (StringUtils.isNotBlank(country)) {
            if (formatted.length() > 0) {
                formatted.append(", ");
            }
            formatted.append(country.trim());
        }
        
        return formatted.toString();
    }
    
    /**
     * Get formatted address as multiple lines
     *
     * @return formatted multi-line address
     */
    @JsonIgnore
    public String getFormattedMultiLine() {
        StringBuilder formatted = new StringBuilder();
        
        // Add recipient name
        String fullName = getFullName();
        if (StringUtils.isNotBlank(fullName)) {
            formatted.append(fullName).append("\n");
        }
        
        // Add company if present
        if (StringUtils.isNotBlank(company)) {
            formatted.append(company.trim()).append("\n");
        }
        
        // Add street address
        if (StringUtils.isNotBlank(streetAddress)) {
            formatted.append(streetAddress.trim()).append("\n");
        }
        
        if (StringUtils.isNotBlank(streetAddress2)) {
            formatted.append(streetAddress2.trim()).append("\n");
        }
        
        // Add city, state, postal code
        StringBuilder cityLine = new StringBuilder();
        if (StringUtils.isNotBlank(city)) {
            cityLine.append(city.trim());
        }
        
        if (StringUtils.isNotBlank(state)) {
            if (cityLine.length() > 0) {
                cityLine.append(", ");
            }
            cityLine.append(state.trim());
        }
        
        if (StringUtils.isNotBlank(postalCode)) {
            if (cityLine.length() > 0) {
                cityLine.append(" ");
            }
            cityLine.append(postalCode.trim());
        }
        
        if (cityLine.length() > 0) {
            formatted.append(cityLine).append("\n");
        }
        
        // Add country
        if (StringUtils.isNotBlank(country)) {
            formatted.append(country.trim());
        }
        
        return formatted.toString().trim();
    }
    
    /**
     * Check if this address is complete and valid for shipping
     *
     * @return true if address is complete
     */
    @JsonIgnore
    public boolean isComplete() {
        return StringUtils.isNotBlank(firstName) &&
               StringUtils.isNotBlank(lastName) &&
               StringUtils.isNotBlank(streetAddress) &&
               StringUtils.isNotBlank(city) &&
               StringUtils.isNotBlank(state) &&
               StringUtils.isNotBlank(postalCode) &&
               StringUtils.isNotBlank(country);
    }
    
    /**
     * Check if this is a domestic address (same country as business)
     *
     * @param businessCountry the business country code
     * @return true if domestic
     */
    @JsonIgnore
    public boolean isDomestic(String businessCountry) {
        return StringUtils.isNotBlank(country) && 
               StringUtils.isNotBlank(businessCountry) &&
               country.equalsIgnoreCase(businessCountry);
    }
    
    /**
     * Normalize the address (trim whitespace, uppercase country code, etc.)
     */
    @JsonIgnore
    public void normalize() {
        if (firstName != null) {
            firstName = firstName.trim();
        }
        if (lastName != null) {
            lastName = lastName.trim();
        }
        if (company != null) {
            company = company.trim();
        }
        if (streetAddress != null) {
            streetAddress = streetAddress.trim();
        }
        if (streetAddress2 != null) {
            streetAddress2 = streetAddress2.trim();
        }
        if (city != null) {
            city = city.trim();
        }
        if (state != null) {
            state = state.trim();
        }
        if (postalCode != null) {
            postalCode = postalCode.trim().toUpperCase();
        }
        if (country != null) {
            country = country.trim().toUpperCase();
        }
        if (phone != null) {
            phone = phone.trim();
        }
        if (email != null) {
            email = email.trim().toLowerCase();
        }
        if (deliveryInstructions != null) {
            deliveryInstructions = deliveryInstructions.trim();
        }
        if (addressType != null) {
            addressType = addressType.trim().toUpperCase();
        }
    }
}
