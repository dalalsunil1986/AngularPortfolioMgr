/**
 *    Copyright 2019 Sven Loesekann
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at
       http://www.apache.org/licenses/LICENSE-2.0
   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package ch.xxx.manager.entity;

import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("portfolio_to_symbol")
public class PortfolioToSymbolEntity {
	@Id
	private Long id;
	private Long portfolioId;
	private Long symbolId;
	private Long weight;
	private LocalDate changedAt;
	private LocalDate removedAt;

	public LocalDate getChangedAt() {
		return changedAt;
	}
	public void setChangedAt(LocalDate changedAt) {
		this.changedAt = changedAt;
	}
	public LocalDate getRemovedAt() {
		return removedAt;
	}
	public void setRemovedAt(LocalDate removedAt) {
		this.removedAt = removedAt;
	}
	public Long getWeight() {
		return weight;
	}
	public void setWeight(Long weight) {
		this.weight = weight;
	}
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public Long getPortfolioId() {
		return portfolioId;
	}
	public void setPortfolioId(Long portfolioId) {
		this.portfolioId = portfolioId;
	}
	public Long getSymbolId() {
		return symbolId;
	}
	public void setSymbolId(Long symbolId) {
		this.symbolId = symbolId;
	}
	@Override
	public String toString() {
		return "PortfolioToSymbolEntity [id=" + id + ", portfolioId=" + portfolioId + ", symbolId=" + symbolId
				+ ", weight=" + weight + ", changedAt=" + changedAt + ", removedAt=" + removedAt + "]";
	}
	
}
