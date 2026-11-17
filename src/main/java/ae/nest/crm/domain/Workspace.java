package ae.nest.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "workspaces")
@Getter
@Setter
public class Workspace extends BaseEntity {

    @Column(nullable = false)
    private String name;

    /** IANA timezone used for display defaults (e.g. Asia/Dubai, Asia/Kolkata). */
    @Column(nullable = false)
    private String timezone;
}
