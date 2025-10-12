package org.de013.userservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;



@Entity
@Table(name = "permissions")
@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Permission extends BaseEntity {
    
    @Column(unique = true, nullable = false)
    private String name;
    
    private String description;
    
    @Column(name = "resource")
    private String resource;
    
    @Column(name = "action")
    private String action;
    
    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;
}
