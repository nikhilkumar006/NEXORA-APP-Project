package com.nexora.academic;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class AcademicAnalysisService {

    /**
     * Syllabus-aligned course map for the current Semester 3 catalogue.
     * Course outlines follow published SRMIST Regulations 2021 curriculum documents.
     * Exam-weightage data is intentionally not part of this catalogue; it is computed only
     * from indexed question-paper evidence by the controller.
     */
    public List<AcademicTopic> getTopics(String subjectCode) {
        if (subjectCode == null) return new ArrayList<>();
        return switch (subjectCode.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "21CSC201J" -> getDsaTopics();
            case "21MAB201T" -> getTransformsTopics();
            case "21DCS201P" -> getDesignThinkingTopics();
            case "21CSS201T" -> getCoaTopics();
            case "21CSC202J" -> getOperatingSystemsTopics();
            case "21CSC203P" -> getAdvancedProgrammingTopics();
            default -> new ArrayList<>();
        };
    }

    public List<AcademicUnit> getUnits(String subjectCode) {

        List<AcademicTopic> topics = getTopics(subjectCode);
        List<AcademicUnit> units = new ArrayList<>();

        if (topics.isEmpty()) {
            return units;
        }

        String currentUnit = null;
        AcademicUnit currentAcademicUnit = null;

        for (AcademicTopic topic : topics) {

            if (!topic.getUnit().equals(currentUnit)) {

                currentUnit = topic.getUnit();

                currentAcademicUnit = new AcademicUnit(
                        unitDisplayName(subjectCode, currentUnit),
                        "",
                        new ArrayList<>()
                );

                units.add(currentAcademicUnit);
            }

            currentAcademicUnit.getTopics().add(topic);
            if (currentAcademicUnit.getDescription() == null || currentAcademicUnit.getDescription().isBlank()) {
                currentAcademicUnit.setDescription(topic.getDescription() == null ? "" : topic.getDescription());
            }
        }

        // Give every configured unit a concise, real syllabus-topic overview rather than
        // the previous blank unit description / generic fallback text.
        for (AcademicUnit unit : units) {
            String overview = unit.getTopics().stream()
                    .map(AcademicTopic::getTopic)
                    .filter(name -> name != null && !name.isBlank())
                    .collect(java.util.stream.Collectors.joining(" · "));
            unit.setDescription(overview);
        }
        return units;
    }

    private String unitDisplayName(String subjectCode, String unit) {
        int number = 0;
        try { number = Integer.parseInt(unit.replaceAll("(?i)^\\s*unit\\s*", "").trim()); }
        catch (RuntimeException ignored) { return unit; }
        String title = switch (subjectCode == null ? "" : subjectCode.toUpperCase(java.util.Locale.ROOT)) {
            case "21CSC201J" -> switch (number) {
                case 1 -> "Introduction to Programming and Data Structures";
                case 2 -> "List Structures";
                case 3 -> "Stacks and Queues";
                case 4 -> "Trees and Hashing";
                case 5 -> "Graphs";
                default -> "";
            };
            case "21MAB201T" -> switch (number) {
                case 1 -> "Partial Differential Equations";
                case 2 -> "Fourier Series";
                case 3 -> "Applications of Partial Differential Equations";
                case 4 -> "Fourier Transforms";
                case 5 -> "Z-Transforms";
                default -> "";
            };
            case "21DCS201P" -> switch (number) {
                case 1 -> "Introduction to Design Methodology";
                case 2 -> "Prototyping";
                case 3 -> "User Assessment";
                case 4 -> "Value Proposition Design";
                case 5 -> "Business Model";
                default -> "";
            };
            case "21CSS201T" -> switch (number) {
                case 1 -> "Number Systems and Logic Gates";
                case 2 -> "Basic Structure of Computers";
                case 3 -> "Design of the ALU";
                case 4 -> "Control Unit and Pipelining";
                case 5 -> "Parallelism and ARM Architecture";
                default -> "";
            };
            case "21CSC202J" -> switch (number) {
                case 1 -> "Operating-System Fundamentals";
                case 2 -> "Process Management and Synchronization";
                case 3 -> "CPU Scheduling and Deadlocks";
                case 4 -> "Memory, Storage and File Systems";
                case 5 -> "Protection and Security";
                default -> "";
            };
            case "21CSC203P" -> switch (number) {
                case 1 -> "Introduction to Programming Paradigms";
                case 2 -> "Java Programming Paradigms";
                case 3 -> "Advanced Java Programming";
                case 4 -> "Python Programming Paradigms";
                case 5 -> "Formal and Symbolic Programming";
                default -> "";
            };
            default -> "";
        };
        return title.isBlank() ? unit : unit + " — " + title;
    }

    private AcademicTopic syllabusTopic(String unit, String name, String description, String... subtopics) {
        return new AcademicTopic(unit, name, description, Arrays.asList(subtopics), List.of(), List.of());
    }

    private List<AcademicTopic> getTransformsTopics() {
        List<AcademicTopic> t = new ArrayList<>();
        t.add(syllabusTopic("Unit 1", "Formation of Partial Differential Equations", "Forming a PDE by eliminating arbitrary constants or arbitrary functions.", "Eliminating arbitrary constants", "Eliminating arbitrary functions"));
        t.add(syllabusTopic("Unit 1", "First-Order Partial Differential Equations", "Standard first-order PDE types and Lagrange's linear equation.", "Standard first-order PDE types", "Lagrange's linear equation"));
        t.add(syllabusTopic("Unit 1", "Higher-Order Linear PDEs", "Homogeneous linear partial differential equations of second and higher order with constant coefficients.", "Second-order equations", "Higher-order equations", "Homogeneous equations with constant coefficients"));
        t.add(syllabusTopic("Unit 2", "Fourier Series Conditions and Expansion", "Dirichlet's conditions and the general Fourier-series expansion.", "Dirichlet's conditions", "General Fourier series"));
        t.add(syllabusTopic("Unit 2", "Odd, Even and Half-Range Series", "Fourier series for odd and even functions and half-range sine/cosine expansions.", "Odd functions", "Even functions", "Half-range sine series", "Half-range cosine series"));
        t.add(syllabusTopic("Unit 2", "Parseval's Identity and Harmonic Analysis", "Parseval's identity and harmonic analysis using Fourier series.", "Parseval's identity", "Harmonic analysis"));
        t.add(syllabusTopic("Unit 3", "Classification and Separation of Variables", "Classification of second-order PDEs and the method of separation of variables.", "Classification of second-order PDEs", "Separation of variables"));
        t.add(syllabusTopic("Unit 3", "One-Dimensional Wave Equation", "Solving the one-dimensional wave equation using PDE and Fourier-series methods.", "One-dimensional wave equation"));
        t.add(syllabusTopic("Unit 3", "Heat Equation and Boundary Conditions", "One-dimensional heat conduction with zero and non-zero steady-state boundary conditions.", "One-dimensional heat-conduction equation", "Steady state with zero boundary conditions", "Steady state with non-zero boundary conditions"));
        t.add(syllabusTopic("Unit 4", "Fourier Transform Pair and Properties", "Fourier transform pair and its properties.", "Fourier transform pair", "Properties of Fourier transforms"));
        t.add(syllabusTopic("Unit 4", "Fourier Sine and Cosine Transforms", "Fourier sine/cosine transforms and transforms of simple functions.", "Fourier sine transform", "Fourier cosine transform", "Transforms of simple functions"));
        t.add(syllabusTopic("Unit 4", "Convolution and Parseval's Identity", "Convolution theorem and Parseval's identity for Fourier transforms.", "Convolution theorem (without proof)", "Parseval's identity"));
        t.add(syllabusTopic("Unit 5", "Z-Transforms and Properties", "Z-transforms and their properties for discrete-time problems.", "Z-transform definition", "Properties of Z-transforms"));
        t.add(syllabusTopic("Unit 5", "Inverse Z-Transforms", "Using inverse Z-transform methods to recover discrete-time sequences.", "Inverse Z-transforms"));
        t.add(syllabusTopic("Unit 5", "Convolution and Difference Equations", "Convolution theorem and solutions of linear constant-coefficient difference equations using Z-transforms.", "Convolution theorem (without proof)", "Linear difference equations with constant coefficients"));
        return t;
    }

    private List<AcademicTopic> getDesignThinkingTopics() {
        List<AcademicTopic> t = new ArrayList<>();
        t.add(syllabusTopic("Unit 1", "Design Frameworks and Problem Solving", "Design methodology, engineering-design problem solving, and development, creation and evaluation of solutions.", "Design frameworks", "Engineering-design problem solving", "Developing design solutions", "Making design solutions", "Evaluating design solutions"));
        t.add(syllabusTopic("Unit 1", "Project Framing and Team Ideation", "Project introduction, team formation, stakeholder mapping and brainstorming.", "Project introduction and team formation", "Stakeholder map", "Brainstorming"));
        t.add(syllabusTopic("Unit 2", "Prototyping Planning and Refinement", "Planning prototypes and refining concepts through storyboards and conceptual design.", "Prototyping planning", "Concept refinement", "Storyboard", "Conceptual design"));
        t.add(syllabusTopic("Unit 2", "Creative Concept-Generation Tools", "Methods for generating concepts and imagining alternative futures.", "Envisioning future", "Creative matrix", "Morphological synthesis", "Concept poster", "Basic presentation"));
        t.add(syllabusTopic("Unit 2", "Usability-Test Demonstration", "Prepare and discuss prototypes with the teaching team and demonstrate usability testing.", "Project discussion with teaching team", "Usability-test demo"));
        t.add(syllabusTopic("Unit 3", "Understanding Users and Customers", "Learn about users and customers to inform solution evaluation.", "Understanding users", "Learning about customers"));
        t.add(syllabusTopic("Unit 3", "Usability Testing and User Assessment", "Assess and test solutions with users.", "Usability test", "User testing", "Project discussion with teaching team"));
        t.add(syllabusTopic("Unit 3", "Clustering and Abstract Laddering", "Structure user research insights through clustering and abstract laddering.", "Clustering", "Abstract laddering"));
        t.add(syllabusTopic("Unit 4", "Value Proposition Design and Mapping", "Develop and map a value proposition for the selected solution.", "Value proposition design", "Value proposition mapping"));
        t.add(syllabusTopic("Unit 4", "Competitor Study and Design Methods", "Compare competitors and complementors and apply design methods.", "Prototyping and competitor study", "Competitors/complementors map", "Design methodologies"));
        t.add(syllabusTopic("Unit 4", "Risk Analysis and Project Showcase", "Explore capital-budgeting risk scenarios and present a project mini-showcase.", "Capital budgeting", "Risk analysis with scenarios", "Project mini-showcase", "Project discussion with teaching team"));
        t.add(syllabusTopic("Unit 5", "Business Model Canvas", "Define the business model and its key components.", "Business Model Canvas"));
        t.add(syllabusTopic("Unit 5", "Business Pitch and Partnerships", "Present the project and plan pitching, intellectual property and partnerships.", "Business pitch", "Pitching strategies", "Intellectual property", "Partnerships"));
        t.add(syllabusTopic("Unit 5", "Financial Forecasting and Final Showcase", "Forecast financial statements and prepare the final project presentation.", "Forecasting financial statements", "Final project presentation", "Design showcase"));
        return t;
    }

    private List<AcademicTopic> getCoaTopics() {
        List<AcademicTopic> t = new ArrayList<>();
        t.add(syllabusTopic("Unit 1", "Number Systems and Codes", "Number representations and common digital codes.", "Binary, decimal, octal and hexadecimal", "Gray code", "BCD", "Excess-3", "ASCII", "Parity codes"));
        t.add(syllabusTopic("Unit 1", "Binary and BCD Arithmetic", "Arithmetic operations using signed representations and BCD.", "Addition and subtraction", "Multiplication and division", "Sign magnitude", "One's complement", "Two's complement", "BCD arithmetic"));
        t.add(syllabusTopic("Unit 1", "Logic Gates", "Fundamental logic gates and their truth-table behavior.", "AND", "OR", "NOT", "NAND", "NOR", "EX-OR", "EX-NOR"));
        t.add(syllabusTopic("Unit 2", "Functional Units and Bus Structures", "Core computer components, operational concepts and bus organization.", "Functional units of a computer", "Operational concepts", "Bus structures"));
        t.add(syllabusTopic("Unit 2", "Memory and Instruction Execution", "Memory addressing/operations, assembly language and instruction sequencing.", "Memory addresses and operations", "Assembly language", "Instructions", "Instruction sequencing", "Addressing modes"));
        t.add(syllabusTopic("Unit 2", "8086 Case Study", "Case study of the 8086 processor as specified by the syllabus.", "8086 architecture case study"));
        t.add(syllabusTopic("Unit 3", "Boolean Algebra and Adders", "Logic design principles and adder structures used in the ALU.", "De Morgan's theorem", "Adders"));
        t.add(syllabusTopic("Unit 3", "Multiplication and Division Hardware", "Unsigned, signed and fast multipliers, carry-save addition, and restoring/non-restoring division.", "Unsigned multiplier", "Signed multiplier", "Fast multiplier", "Carry-save addition of summands", "Restoring division", "Non-restoring division"));
        t.add(syllabusTopic("Unit 3", "IEEE 754 Floating-Point Operations", "Floating-point number representation and arithmetic under IEEE 754.", "IEEE 754 representation", "Floating-point operations"));
        t.add(syllabusTopic("Unit 4", "Basic Processing Unit and Instruction Execution", "ALU operations, branch instructions, instruction execution and multiple-bus organization.", "Basic processing unit", "ALU operations", "Instruction execution", "Branch instructions", "Multiple-bus organization"));
        t.add(syllabusTopic("Unit 4", "Control-Unit Design", "Hardwired and micro-programmed control and generation of control signals.", "Hardwired control", "Control-signal generation", "Micro-programmed control"));
        t.add(syllabusTopic("Unit 4", "Pipelining and Hazards", "Pipeline performance and instruction-set influences, including data, instruction and control hazards.", "Pipelining basics", "Pipeline performance", "Data hazards", "Instruction hazards", "Control hazards", "Influence on instruction sets"));
        t.add(syllabusTopic("Unit 5", "Parallelism and Flynn's Classification", "Need, types, applications and challenges of parallelism and parallel-system architectures.", "Need for parallelism", "Types and applications", "Challenges", "Flynn's classification"));
        t.add(syllabusTopic("Unit 5", "ARM Processor Architecture", "ARM processor concepts, thumb instruction set, processor cores and instruction encoding.", "Thumb instruction set", "Processor and CPU cores", "Instruction encoding format"));
        t.add(syllabusTopic("Unit 5", "ARM Memory and I/O", "Memory load/store instructions, basic I/O operations and ARM 5/ARM 7 case studies.", "Memory load and store instructions", "Basics of I/O operations", "ARM 5 architecture case study", "ARM 7 architecture case study"));
        return t;
    }

    private List<AcademicTopic> getOperatingSystemsTopics() {
        List<AcademicTopic> t = new ArrayList<>();
        t.add(syllabusTopic("Unit 1", "Operating-System Fundamentals", "Purpose, structure and operation of operating systems and the computer environments in which they run.", "Introduction", "Computer-system organization", "Computer-system architecture", "Operating-system structure", "Operating-system operations", "Computing environments", "Open-source operating systems"));
        t.add(syllabusTopic("Unit 1", "OS Services and Interfaces", "Operating-system services, system calls and user/kernel interfaces.", "Operating-system services", "User and operating-system interface", "System calls and types", "System programs", "OS design and implementation", "OS debugging", "OS generation", "System boot"));
        t.add(syllabusTopic("Unit 1", "Resource and Security Overview", "The operating system's process, memory, storage and protection responsibilities.", "Process management", "Memory management", "Storage management", "Protection and security", "Kernel data structures"));
        t.add(syllabusTopic("Unit 2", "Processes and Interprocess Communication", "Process concepts, scheduling, operations and communication between processes and client/server systems.", "Process concept", "Process scheduling", "Operations on processes", "Interprocess communication", "Client-server communication"));
        t.add(syllabusTopic("Unit 2", "Threads and Multicore Programming", "Thread models, libraries and implicit threading on multicore systems.", "Multicore programming", "Multithreading models", "Thread libraries", "Implicit threading", "Threading issues"));
        t.add(syllabusTopic("Unit 2", "Process Synchronization", "Critical sections, synchronization hardware and standard synchronization mechanisms.", "Critical-section problem", "Peterson's solution", "Synchronization hardware", "Mutex locks", "Semaphores", "Classic synchronization problems", "Monitors"));
        t.add(syllabusTopic("Unit 3", "CPU Scheduling", "Criteria and algorithms for scheduling processors and threads.", "Scheduling criteria", "Scheduling algorithms", "Thread scheduling", "Multiprocessor scheduling", "Real-time CPU scheduling"));
        t.add(syllabusTopic("Unit 3", "Deadlock Management", "Deadlock models, characterization and prevention/avoidance/detection/recovery methods.", "System model", "Deadlock characterization", "Methods for handling deadlocks", "Prevention", "Avoidance", "Detection", "Recovery"));
        t.add(syllabusTopic("Unit 4", "Main-Memory Management", "Memory allocation and mapping techniques.", "Main memory", "Swapping", "Contiguous memory allocation", "Segmentation", "Paging", "Page-table structure"));
        t.add(syllabusTopic("Unit 4", "Virtual Memory", "Demand paging, replacement policies, frame allocation and memory pressure.", "Demand paging", "Copy-on-write", "Page replacement", "Frame allocation", "Thrashing", "Memory-mapped files", "Kernel-memory allocation"));
        t.add(syllabusTopic("Unit 4", "Storage and File Systems", "Disk and swap management, RAID, file interfaces, directories, mounting, sharing and protection.", "Disk structure and attachment", "Disk scheduling", "Disk management", "Swap-space management", "RAID", "File concept and access methods", "Directory/disk structure", "File-system mounting", "File sharing", "File protection"));
        t.add(syllabusTopic("Unit 5", "Protection Models and Access Control", "Protection goals, domains, access matrices and revocation of access rights.", "Protection goals and principles", "Domain of protection", "Access matrix", "Access-matrix implementation", "Access control", "Revocation of access rights", "Capability-based systems", "Language-based protection"));
        t.add(syllabusTopic("Unit 5", "Computer and Network Security", "Threats and security mechanisms for protecting operating systems and networks.", "Security problem", "Program threats", "System and network threats", "Cryptography as a security tool", "User authentication", "Security defenses", "Firewalls", "Computer-security classifications"));
        return t;
    }

    private List<AcademicTopic> getAdvancedProgrammingTopics() {
        List<AcademicTopic> t = new ArrayList<>();
        t.add(syllabusTopic("Unit 1", "Programming Languages and Paradigms", "Programming-language elements, theory, structured-program theorem and paradigm hierarchy.", "Programming languages and elements", "Programming-language theory", "Böhm–Jacopini structured program theorem", "Multiple paradigms", "Paradigm hierarchy"));
        t.add(syllabusTopic("Unit 1", "Imperative and Declarative Paradigms", "Imperative, procedural, object-oriented, parallel, logic, functional and database-processing paradigms.", "Procedural programming", "Object-oriented programming", "Parallel processing", "Logic programming", "Functional programming", "Database processing"));
        t.add(syllabusTopic("Unit 1", "Runtime and Object Concepts", "Paradigm suitability, call overhead, dynamic memory for messages/objects, serialization and parallel computing.", "Machine codes", "Paradigm suitability", "Subroutine/method-call overhead", "Dynamic memory allocation", "Message/object storage", "Dynamic dispatch", "Object serialization", "Parallel computing"));
        t.add(syllabusTopic("Unit 2", "Java Language Fundamentals", "Core Java constructs and class-based program structure.", "Objects and classes", "Constructors", "Data types", "Variables", "Modifiers", "Operators"));
        t.add(syllabusTopic("Unit 2", "Structural and Procedural Java", "Branching, iteration, decisions, arrays and function-like procedural constructs.", "Branching", "Iteration", "Decision making", "Arrays", "Function definition", "Function declaration and calling", "Function arguments"));
        t.add(syllabusTopic("Unit 2", "Object-Oriented Java and Packages", "Object-oriented principles and interface/package creation.", "Abstraction", "Encapsulation", "Inheritance", "Polymorphism", "Overriding", "Declaring and implementing interfaces", "Extended/tagging interfaces", "Package creation"));
        t.add(syllabusTopic("Unit 3", "Concurrent Programming", "Multithreading and multitasking using thread classes and methods.", "Multithreading", "Multitasking", "Thread classes", "Thread methods"));
        t.add(syllabusTopic("Unit 3", "JDBC and MySQL", "Database connectivity with MySQL and query execution through JDBC.", "JDBC", "MySQL connectivity", "Query execution"));
        t.add(syllabusTopic("Unit 3", "Java GUI and Project", "Graphical user interfaces with Swing, MVC, widgets and a Java project.", "Java applet basics (as listed in the source syllabus)", "Java Swing", "Model-View-Controller", "GUI widgets", "Programming-paradigm project"));
        t.add(syllabusTopic("Unit 4", "Functional and Logic Programming in Python", "Functional programming, pure functions, higher-order functions, logic structures and control.", "Functional-programming concepts", "Pure functions", "Built-in higher-order functions", "Logic structures", "Logic control"));
        t.add(syllabusTopic("Unit 4", "Parallel and Multiprocessing Python", "Parallel programming concepts and multiprocessing with IPython.", "Shared memory", "Distributed memory", "Multiprocessing", "IPython"));
        t.add(syllabusTopic("Unit 4", "Python Socket Programming", "Socket creation/configuration and TCP/UDP client-server networking.", "Socket programming", "Socket types", "TCP sockets", "UDP sockets", "Client-server model"));
        t.add(syllabusTopic("Unit 5", "Automata-Based Programming", "Finite automata concepts and implementation with an automaton library.", "Finite automata", "DFA", "NFA", "Automaton library"));
        t.add(syllabusTopic("Unit 5", "Symbolic Programming with SymPy", "Algebraic manipulation and calculus using a symbolic mathematics library.", "Algebraic manipulation", "Symbolic calculus", "SymPy library"));
        t.add(syllabusTopic("Unit 5", "Event-Driven GUI with Tkinter", "Event handlers, trigger functions and events in Tkinter.", "Event handlers", "Trigger functions", "Events", "Tkinter library", "Python project"));
        return t;
    }

    private List<AcademicTopic> getDsaTopics() {

        List<AcademicTopic> topics = new ArrayList<>();

        /*
         * ========================================================
         * UNIT 1
         * ========================================================
         */

        topics.add(
                new AcademicTopic(
                        "Unit 1",
                        "Programming in C and Primitive Data Types",
                        "Introduction to C programming, program structure, identifiers, variables, constants and primitive data types.",
                        Arrays.asList(
                                "Programming in C - Background",
                                "Structure of a C Program",
                                "Comments",
                                "Identifiers",
                                "Keywords",
                                "Variables",
                                "Variable Declarations and Initialization",
                                "Constants",
                                "Escape Sequences",
                                "Data Types",
                                "Primitive Data Types",
                                "sizeof Operator"
                        ),
                        Arrays.asList(),
                        Arrays.asList(
                                "Structures",
                                "Matrix Multiplication",
                                "Dynamic Memory Allocation"
                        )
                )
        );

        topics.add(
                new AcademicTopic(
                        "Unit 1",
                        "Structures",
                        "User-defined data type used to group logically related data items of different data types under one name.",
                        Arrays.asList(
                                "User Defined Datatypes",
                                "Structure Definition",
                                "Structure Variables",
                                "Structure Member Access",
                                "Dot Operator",
                                "Arrow Operator",
                                "Array of Structures",
                                "Structures with Functions",
                                "Nested Structures",
                                "Self-Referential Structures",
                                "Structure Pointers"
                        ),
                        Arrays.asList(
                                "Programming in C and Primitive Data Types"
                        ),
                        Arrays.asList(
                                "Self-Referential Structures",
                                "Dynamic Memory Allocation"
                        )
                )
        );

        topics.add(
                new AcademicTopic(
                        "Unit 1",
                        "Matrix Multiplication",
                        "Matrix multiplication using row-by-column multiplication and C programming constructs such as arrays, functions and pointers.",
                        Arrays.asList(
                                "Matrix Representation",
                                "Matrix Multiplication Definition",
                                "Row-by-Column Multiplication",
                                "Matrix Multiplication Algorithm",
                                "Matrix Multiplication using Arrays",
                                "Matrix Multiplication using Dynamic Memory Allocation"
                        ),
                        Arrays.asList(
                                "Programming in C and Primitive Data Types"
                        ),
                        Arrays.asList(
                                "Dynamic Memory Allocation",
                                "Algorithm Analysis"
                        )
                )
        );

        topics.add(
                new AcademicTopic(
                        "Unit 1",
                        "Algorithm Analysis",
                        "Study of algorithms using input size, primitive operations, running time and efficiency measures.",
                        Arrays.asList(
                                "Algorithmic Problem",
                                "Algorithmic Solution",
                                "Criteria of a Good Algorithm",
                                "Running Time Measurement",
                                "Experimental Study",
                                "Limitations of Experimental Study",
                                "Pseudo Code",
                                "Primitive Operations",
                                "Sorting Example",
                                "Insertion Sort",
                                "Best Case",
                                "Worst Case",
                                "Average Case",
                                "Asymptotic Analysis"
                        ),
                        Arrays.asList(
                                "Programming in C and Primitive Data Types"
                        ),
                        Arrays.asList(
                                "Time Complexity",
                                "Space Complexity",
                                "Asymptotic Notations"
                        )
                )
        );

        topics.add(
                new AcademicTopic(
                        "Unit 1",
                        "Dynamic Memory Allocation",
                        "Runtime memory allocation used when the required amount of memory cannot be fixed before program execution.",
                        Arrays.asList(
                                "Problem with Arrays",
                                "Dynamic Memory Allocation",
                                "malloc",
                                "calloc",
                                "free",
                                "realloc",
                                "Memory Allocation Failure",
                                "NULL Pointer from malloc"
                        ),
                        Arrays.asList(
                                "Programming in C and Primitive Data Types",
                                "Pointers"
                        ),
                        Arrays.asList(
                                "Matrix Multiplication",
                                "Self-Referential Structures"
                        )
                )
        );

        topics.add(
                new AcademicTopic(
                        "Unit 1",
                        "Mathematical Notations",
                        "Mathematical functions and notations used for analysing algorithms and expressing computational relationships.",
                        Arrays.asList(
                                "Floor and Ceiling Functions",
                                "Remainder Function",
                                "Modular Arithmetic",
                                "Integer Function",
                                "Absolute Value Function",
                                "Summation",
                                "Factorial Function",
                                "Permutations",
                                "Exponents",
                                "Logarithms"
                        ),
                        Arrays.asList(),
                        Arrays.asList(
                                "Asymptotic Notations",
                                "Time Complexity"
                        )
                )
        );

        topics.add(
                new AcademicTopic(
                        "Unit 1",
                        "Asymptotic Notations",
                        "Mathematical tools used to represent the growth of algorithm running time with increasing input size.",
                        Arrays.asList(
                                "Best Case",
                                "Average Case",
                                "Worst Case",
                                "Big-O Notation",
                                "Big-Omega Notation",
                                "Theta Notation",
                                "Little-o Notation",
                                "Little-omega Notation",
                                "Asymptotic Order",
                                "Properties of O, Omega and Theta"
                        ),
                        Arrays.asList(
                                "Algorithm Analysis",
                                "Mathematical Notations"
                        ),
                        Arrays.asList(
                                "Time Complexity",
                                "Space Complexity"
                        )
                )
        );

        topics.add(
                new AcademicTopic(
                        "Unit 1",
                        "Space Complexity",
                        "Analysis of the memory space required by an algorithm or program, including fixed and variable components.",
                        Arrays.asList(
                                "Space Complexity Definition",
                                "Fixed Part",
                                "Variable Part",
                                "Space Requirement",
                                "Constant Space",
                                "Linear Space",
                                "Space Complexity of Programs"
                        ),
                        Arrays.asList(
                                "Programming in C and Primitive Data Types",
                                "Algorithm Analysis"
                        ),
                        Arrays.asList(
                                "Time Complexity"
                        )
                )
        );

        topics.add(
                new AcademicTopic(
                        "Unit 1",
                        "Time Complexity",
                        "Analysis of the amount of computational time required by an algorithm as a function of input size.",
                        Arrays.asList(
                                "Time Complexity Definition",
                                "T(n)",
                                "Constant Time Complexity",
                                "Linear Time Complexity",
                                "Quadratic Time Complexity",
                                "Count Variable Method",
                                "Table Method",
                                "Step Count",
                                "Cost and Frequency",
                                "Insertion Sort Time Complexity",
                                "Best Case",
                                "Worst Case",
                                "Average Case"
                        ),
                        Arrays.asList(
                                "Algorithm Analysis",
                                "Asymptotic Notations"
                        ),
                        Arrays.asList(
                                "Space Complexity",
                                "Searching Techniques"
                        )
                )
        );

        topics.add(
                new AcademicTopic(
                        "Unit 1",
                        "Data Structure Operations",
                        "Fundamental operations used to process and manipulate data stored in data structures.",
                        Arrays.asList(
                                "Traversing",
                                "Searching",
                                "Inserting",
                                "Updating",
                                "Deleting",
                                "Sorting",
                                "Merging"
                        ),
                        Arrays.asList(
                                "Data Structure Fundamentals"
                        ),
                        Arrays.asList(
                                "Abstract Data Types",
                                "Searching Techniques"
                        )
                )
        );

        topics.add(
                new AcademicTopic(
                        "Unit 1",
                        "Abstract Data Types",
                        "A data type defined by its values and operations while hiding implementation details.",
                        Arrays.asList(
                                "ADT Definition",
                                "Abstraction",
                                "ADT Operations",
                                "Create",
                                "Display",
                                "Insert",
                                "Delete",
                                "Modify",
                                "ADT Model",
                                "Functions and Data Structures",
                                "Interface"
                        ),
                        Arrays.asList(
                                "Data Structure Operations"
                        ),
                        Arrays.asList(
                                "Stack",
                                "Queue ADT",
                                "Linked List"
                        )
                )
        );

        topics.add(
                new AcademicTopic(
                        "Unit 1",
                        "Searching Techniques",
                        "Algorithms used to locate or retrieve an element from a data structure.",
                        Arrays.asList(
                                "Searching Algorithms",
                                "Linear Search",
                                "Linear Search Algorithm",
                                "Linear Search Advantages",
                                "Linear Search Disadvantages",
                                "Binary Search",
                                "Binary Search Algorithm",
                                "Binary Search Advantages",
                                "Binary Search Disadvantages",
                                "Linear Search vs Binary Search"
                        ),
                        Arrays.asList(
                                "Data Structure Operations",
                                "Time Complexity"
                        ),
                        Arrays.asList(
                                "Sorting",
                                "Searching"
                        )
                )
        );

        /*
         * ========================================================
         * UNIT 2
         * ========================================================
         */

        topics.add(
                new AcademicTopic(
                        "Unit 2",
                        "Linked List",
                        "Dynamic linear data structure and its operations.",
                        Arrays.asList(
                                "Pointers",
                                "Structures",
                                "Arrays"
                        ),
                        Arrays.asList(
                                "Singly linked list",
                                "Doubly linked list",
                                "Circular linked list"
                        )
                )
        );

        topics.add(
                new AcademicTopic(
                        "Unit 2",
                        "Singly Linked List",
                        "Linked list containing nodes connected in one direction.",
                        Arrays.asList(
                                "Pointers",
                                "Structures",
                                "Linked List"
                        ),
                        Arrays.asList(
                                "Insertion",
                                "Deletion",
                                "Traversal"
                        )
                )
        );

        topics.add(
                new AcademicTopic(
                        "Unit 2",
                        "Doubly Linked List",
                        "Linked list where every node maintains links to both previous and next nodes.",
                        Arrays.asList(
                                "Pointers",
                                "Structures",
                                "Singly linked list"
                        ),
                        Arrays.asList(
                                "Insertion",
                                "Deletion",
                                "Traversal"
                        )
                )
        );

        /*
         * ========================================================
         * UNIT 3
         * ========================================================
         */

        topics.add(
                new AcademicTopic(
                        "Unit 3",
                        "Queue ADT",
                        "FIFO-based abstract data type and its fundamental operations.",
                        Arrays.asList(
                                "Arrays",
                                "Linked List"
                        ),
                        Arrays.asList(
                                "Enqueue",
                                "Dequeue",
                                "Queue applications"
                        )
                )
        );

        topics.add(
                new AcademicTopic(
                        "Unit 3",
                        "Circular Queue",
                        "Queue implementation where the final position connects back to the first position.",
                        Arrays.asList(
                                "Queue ADT",
                                "Arrays"
                        ),
                        Arrays.asList(
                                "Queue operations",
                                "Circular queue implementation"
                        )
                )
        );

        topics.add(
                new AcademicTopic(
                        "Unit 3",
                        "Priority Queue",
                        "Queue structure where elements are processed according to priority.",
                        Arrays.asList(
                                "Queue ADT"
                        ),
                        Arrays.asList(
                                "Priority-based processing",
                                "Queue applications"
                        )
                )
        );

        topics.add(
                new AcademicTopic(
                        "Unit 3",
                        "Infix to Postfix Conversion",
                        "Conversion of infix expressions into postfix notation using stack-based processing.",
                        Arrays.asList(
                                "Stack",
                                "Operators",
                                "Expression evaluation"
                        ),
                        Arrays.asList(
                                "Postfix evaluation",
                                "Stack applications"
                        )
                )
        );

        topics.add(
                new AcademicTopic(
                        "Unit 3",
                        "Tower of Hanoi",
                        "Recursive problem involving movement of disks between rods under defined constraints.",
                        Arrays.asList(
                                "Recursion",
                                "Function calls"
                        ),
                        Arrays.asList(
                                "Recursion",
                                "Algorithm analysis"
                        )
                )
        );

        /*
         * ========================================================
         * UNIT 4
         * ========================================================
         */

        topics.add(
                new AcademicTopic(
                        "Unit 4",
                        "Trees",
                        "Hierarchical data structure based on linked nodes and recursive relationships.",
                        Arrays.asList(
                                "Linked structures",
                                "Recursion"
                        ),
                        Arrays.asList(
                                "Binary tree",
                                "BST",
                                "Tree traversal"
                        )
                )
        );

        topics.add(
                new AcademicTopic(
                        "Unit 4",
                        "Binary Tree",
                        "Tree data structure in which nodes have at most two children.",
                        Arrays.asList(
                                "Trees",
                                "Recursion"
                        ),
                        Arrays.asList(
                                "Tree traversal",
                                "Binary Search Tree"
                        )
                )
        );

        topics.add(
                new AcademicTopic(
                        "Unit 4",
                        "Binary Search Tree",
                        "Binary tree organized according to ordering rules to support searching and related operations.",
                        Arrays.asList(
                                "Binary Tree",
                                "Recursion"
                        ),
                        Arrays.asList(
                                "Searching",
                                "Insertion",
                                "Deletion"
                        )
                )
        );

        topics.add(
                new AcademicTopic(
                        "Unit 4",
                        "AVL Tree",
                        "Self-balancing binary search tree using tree rotations.",
                        Arrays.asList(
                                "Binary Search Tree",
                                "Tree rotations"
                        ),
                        Arrays.asList(
                                "Balancing",
                                "Rotations"
                        )
                )
        );

        topics.add(
                new AcademicTopic(
                        "Unit 4",
                        "Tree Traversals",
                        "Methods for visiting nodes of a tree in defined orders.",
                        Arrays.asList(
                                "Trees",
                                "Recursion"
                        ),
                        Arrays.asList(
                                "Preorder",
                                "Inorder",
                                "Postorder"
                        )
                )
        );

        topics.add(
                new AcademicTopic(
                        "Unit 4",
                        "Hashing",
                        "Technique for mapping keys to positions using hash functions and collision handling.",
                        Arrays.asList(
                                "Arrays",
                                "Searching"
                        ),
                        Arrays.asList(
                                "Hash functions",
                                "Collision handling"
                        )
                )
        );

        /*
         * ========================================================
         * UNIT 5
         * ========================================================
         */

        topics.add(
                new AcademicTopic(
                        "Unit 5",
                        "Graphs",
                        "Data structure used to represent relationships between vertices.",
                        Arrays.asList(
                                "Trees",
                                "Data structures fundamentals"
                        ),
                        Arrays.asList(
                                "Graph representation",
                                "Graph traversal"
                        )
                )
        );

        topics.add(
                new AcademicTopic(
                        "Unit 5",
                        "Graph Traversal",
                        "Methods for systematically visiting vertices of a graph.",
                        Arrays.asList(
                                "Graphs",
                                "Queue",
                                "Stack"
                        ),
                        Arrays.asList(
                                "BFS",
                                "DFS"
                        )
                )
        );

        topics.add(
                new AcademicTopic(
                        "Unit 5",
                        "Topological Sorting",
                        "Ordering of vertices in a directed graph according to dependency relationships.",
                        Arrays.asList(
                                "Graphs",
                                "Directed graphs"
                        ),
                        Arrays.asList(
                                "DFS",
                                "Indegree"
                        )
                )
        );

        topics.add(
                new AcademicTopic(
                        "Unit 5",
                        "Prim's Algorithm",
                        "Algorithm for constructing a minimum spanning tree of a weighted graph.",
                        Arrays.asList(
                                "Graphs",
                                "Weighted graphs"
                        ),
                        Arrays.asList(
                                "Minimum spanning tree",
                                "Priority queue"
                        )
                )
        );

        topics.add(
                new AcademicTopic(
                        "Unit 5",
                        "Kruskal's Algorithm",
                        "Algorithm for constructing a minimum spanning tree by processing weighted edges.",
                        Arrays.asList(
                                "Graphs",
                                "Weighted graphs"
                        ),
                        Arrays.asList(
                                "Minimum spanning tree",
                                "Disjoint sets"
                        )
                )
        );

        topics.add(
                new AcademicTopic(
                        "Unit 5",
                        "Dijkstra's Algorithm",
                        "Algorithm for finding shortest paths from a source vertex in a weighted graph.",
                        Arrays.asList(
                                "Graphs",
                                "Weighted graphs"
                        ),
                        Arrays.asList(
                                "Shortest path",
                                "Priority queue"
                        )
                )
        );

        return topics;
    }
}