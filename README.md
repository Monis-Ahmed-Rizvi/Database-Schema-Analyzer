# Database Schema Analyzer

![Database Schema Analyzer Banner](https://example.com/placeholder-banner.png)

> A powerful tool that analyzes database schemas for normalization issues and provides automatic improvement recommendations. Currently optimized for MySQL with architecture supporting future database system expansion.

## ✨ Features

- **Complete Normalization Analysis**: Analyzes schemas for 1NF, 2NF, and 3NF compliance
- **Intelligent Issue Detection**: Identifies repeating groups, multi-valued attributes, partial dependencies, and transitive dependencies
- **SQL Generation**: Automatically generates SQL to fix detected normalization issues
- **Visual Reporting**: Clean, intuitive interface showing normalization status and issues
- **SQL File Upload**: Upload SQL files directly for analysis
- **Downloadable Results**: Export findings as JSON or text format
- **Interactive UI**: Modern React-based interface with syntax highlighting

## 🖥️ Demo

### Analysis Results
![Analysis Results Screenshot](https://example.com/placeholder-screenshot.png)

### Normalization Issues View
![Normalization Issues Screenshot](https://example.com/placeholder-screenshot2.png)

The Database Schema Analyzer provides comprehensive analysis of schema normalization issues across all three normal forms, with clear visualizations and actionable improvement suggestions.

## 🚀 Technologies

### Backend
- Java 11
- Spring Boot 2.7
- JSqlParser 4.5
- Lombok

### Frontend
- React 19
- React Router 7
- Axios
- Bootstrap 5
- React Syntax Highlighter

## 📋 How It Works

The MySQL Schema Analyzer works in several sophisticated stages:

1. **SQL Parsing**: Uses advanced parsing techniques to convert SQL CREATE TABLE statements into an object model
2. **Schema Analysis**: Implements complex algorithms to detect:
   - First Normal Form (1NF) violations: Missing primary keys, repeating groups, and multi-valued attributes
   - Second Normal Form (2NF) violations: Partial dependencies on composite keys
   - Third Normal Form (3NF) violations: Transitive dependencies between attributes
3. **SQL Generation**: Creates optimized SQL statements to fix normalization issues
4. **Visual Reporting**: Presents findings in an intuitive, user-friendly interface

## 📊 Analysis Capabilities

This tool can accurately detect:

### 1NF Issues
- Missing primary keys
- Repeating columns/groups (e.g., address1, address2, address3)
- Multi-valued attributes (SET, ENUM, JSON fields)

### 2NF Issues
- Partial dependencies in composite keys
- Attributes dependent on only part of a multi-column primary key

### 3NF Issues
- Transitive dependencies
- Non-key attributes dependent on other non-key attributes

## 📦 Installation

### Prerequisites
- JDK 11 or higher
- Node.js 16 or higher
- Maven 3.6 or higher

### Backend Setup
```bash
git clone https://github.com/yourusername/database-schema-analyzer.git
cd database-schema-analyzer
mvn clean install
mvn spring-boot:run
```

### Frontend Setup
```bash
cd mysql-schema-analyzer-frontend
npm install
npm start
```

The application will be available at http://localhost:3000

## 🔍 Usage Examples

### Example 1: Analyzing a Table with 1NF Issues

```sql
CREATE TABLE student_courses (
  student_id INT PRIMARY KEY,
  student_name VARCHAR(100),
  course1 VARCHAR(50),
  course2 VARCHAR(50),
  course3 VARCHAR(50)
);
```

The analyzer will identify the repeating course columns and suggest creating a separate table with a foreign key relationship.

### Example 2: Detecting 3NF Violations

```sql
CREATE TABLE employees (
  employee_id INT PRIMARY KEY,
  name VARCHAR(100),
  department_id INT,
  department_name VARCHAR(100)
);
```

The analyzer will detect that department_name is transitively dependent on employee_id through department_id and suggest creating a separate department table.

## 🏗️ Architecture

The system follows a clean, modular architecture:

1. **Controller Layer**: REST endpoints for schema analysis and improvement generation
2. **Service Layer**: Implements core analysis algorithms and SQL generation
3. **Model Layer**: Represents database schema elements (tables, columns, constraints)
4. **Frontend**: React components with state management and API integration

### Key Components

- **SQLParserService**: Parses SQL scripts into model objects
- **NormalizationService**: Orchestrates the analysis process
- **FirstNormalFormAnalyzer**: Detects 1NF violations
- **SecondNormalFormAnalyzer**: Detects 2NF violations
- **ThirdNormalFormAnalyzer**: Detects 3NF violations

## 🔬 Technical Deep Dive

### Normalization Detection Algorithms

The project implements sophisticated algorithms to detect normalization issues:

- **Repeating Group Detection**: Pattern analysis to identify column naming conventions suggestive of repeating data
- **Multi-valued Attribute Detection**: Type analysis of column definitions to find SET, ENUM, and JSON types
- **Partial Dependency Analysis**: Relationship mapping between primary key parts and non-key attributes
- **Transitive Dependency Detection**: Heuristic algorithms to identify non-key attributes dependent on other non-key attributes

### SQL Generation

The system employs template-based SQL generation with customization based on:
- Original table structure
- Detected issue type
- Data type compatibility
- Foreign key relationships

## 📈 Future Enhancements

- Support for additional database systems (PostgreSQL, Oracle, SQL Server)
- Integration with live database connections
- Support for BCNF and 4NF analysis
- Automated data migration scripts
- Schema visualization tools
- Extensible plugin architecture for custom normalization rules
- Integration with CI/CD pipelines for schema validation
- Database schema version control and change tracking

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

---

## 🔗 Contact

- GitHub: [https://github.com/Monis-Ahmed-Rizvi](https://github.com/Monis-Ahmed-Rizvi)


---

*Note: This project was developed to address real-world database design challenges through advanced normalization analysis algorithms and intuitive visualization of complex schema relationships.*