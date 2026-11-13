# EUDI Wallet Standardization Repository Guide

This document provides a comprehensive breakdown of the **fields, labels, and statuses** used in the EUDI Wallet Standardization Strategy repository and project views.

## **Custom Fields**
The repository uses the following fields to track progress, standardization status, and roadmap timelines:

### **1. Start Date**
- The date when work on the Standard or Technical Specification (STS) began.
- Used for tracking historical development.

### **2. Target Date**
- The estimated completion date when the STS is expected to reach a **stable version** suitable for reference in Implementing Acts.
- This date is subject to change based on progress and external factors.

### **4. Eligibility**
- Defines whether an STS can be referenced in Implementing Acts and through which process.
- **Values:**
  - `SDO`: Comes from a **recognized standardization body** (CEN, ETSI, ISO, etc.) and can be referenced directly.
  - `MSP Pending`: Requires assessment through the **Multi-Stakeholder Platform (MSP)** before referencing.
  - `MSP Approved`: Successfully assessed and approved through the **MSP process** for referencing.
  - `EC`: The **European Commission** conducts a desk-level assessment to determine referencing eligibility.

### **5. Responsible Body**
- The organization developing and maintaining the STS.
- Examples: **ETSI, CEN, ISO, W3C, IETF**.

### **6. Published Date**
- The last stable release date of the STS, if available.
- Used to determine whether an update is required.

### **7. Target Quarter**
- The expected quarter in which a **stable version of the STS** that meets EUDI Wallet requirements will be available.
- Example format: `2025 Q4` (meaning the last quarter of 2025).

### **8. Relevant Standard**
- Specifies whether an STS fulfills the requirements or if further work is needed.
- **Values:**
  - `Candidate`: No gaps identified yet, awaiting final assessment.
  - `Needs update`: A standard exists but requires modifications to meet requirements.
  - `Draft`: A standard is being developed but not yet published.
  - `Missing`: No relevant standard exists, requiring new development.
  - `Under review`: The standard is currently being evaluated to confirm compliance with functional requirements.

## **Status Labels (Project View)**
Issues in the **GitHub Project Board** are categorized based on their progress:

### **1. Backlog**
- The need for technical specifications has been identified as relevant to EUDI Wallet requirements, but no suitable STS is currently available..

### **2. In Progress**
- A relevant STS exists, but gaps have been identified, or the document is incomplete.
- Work is actively being done to address the gaps.

### **3. Done**
- The identified STS has reached a stable version and is currently considered a Candidate, meaning no gaps have been identified, but final assessment is pending.

## **Milestones**
- Issues are grouped into **Milestones**, which cluster related topics for assessment and scheduling.  
- Each **Milestone** aligns with specific **EUDI Wallet standardization needs** and helps structure the evaluation and development process.

## **Labels**
Labels are used to categorize and filter issues based on regulatory references, functional needs, and progress.
- **Regulatory Article Labels**: Indicate which **Implementing Act article** an STS is linked to.
- **Functional Requirement Labels**: Tag issues with relevant **EUDI Wallet architecture and functional framework** components.
- **Status Labels**: Indicate the progress of each STS issue (e.g., `gap needs clarity`, `locked`, `profile ready`).

## **How to Use This Document**
- Use this document to **understand how issues are categorized and tracked**.
- Reference the **GitHub Project Board** ([View Board](https://github.com/orgs/eu-digital-identity-wallet/projects/29)) to see how issues are assigned statuses.
- Filter issues using **labels** in the [Issues List](https://github.com/eu-digital-identity-wallet/ec-internal-standards/issues) to find relevant topics.

For additional clarifications, please participate in **[GitHub Discussions](https://github.com/eu-digital-identity-wallet/eudi-doc-standards-and-technical-specifications/discussions)**.
