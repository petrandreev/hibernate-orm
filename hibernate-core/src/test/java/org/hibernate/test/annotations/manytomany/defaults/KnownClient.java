//$Id$
package org.hibernate.test.annotations.manytomany.defaults;
import java.util.Set;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToMany;

import org.hibernate.test.annotations.manytomany.Store;

/**
 * @author Emmanuel Bernard
 */
@Entity
public class KnownClient {
	private Integer id;
	private String name;
	private Set<Store> stores;

	@Id
	@GeneratedValue
	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@ManyToMany(mappedBy = "customers")
	public Set<Store> getStores() {
		return stores;
	}

	public void setStores(Set<Store> stores) {
		this.stores = stores;
	}
}