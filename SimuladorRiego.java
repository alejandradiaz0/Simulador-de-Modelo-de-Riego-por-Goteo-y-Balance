import javax.swing.*;
import java.awt.*;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;
import java.util.Stack;

public class SimuladorRiego extends JFrame {
    private final double[] caudales = {12.0, 10.0, 8.0};

    private final double[][] coeficientes = {
        {4.0, -1.0, 0.0},
        {-1.0, 4.0, -1.0},
        {0.0, -1.0, 3.0}
    };

    private final ListaSimple historialPresion = new ListaSimple();
    private final ListaDoble historialFugas = new ListaDoble();
    private final ListaCircular sectores = new ListaCircular();
    private final Stack<double[]> pilaPresiones = new Stack<>();
    private final Queue<String> colaHumedad = new ArrayDeque<>();

    private double[] ultimaPresion = {0.0, 0.0, 0.0};
    private final JTextArea salida = new JTextArea(18, 58);

    private final JTextField campoSector = new JTextField("Sector A", 18);
    private final JTextField campoCaudal = new JTextField("12", 18);
    private final JTextField campoHumedad = new JTextField("35", 18);
    private final JTextArea campoObservaciones = new JTextArea(3, 18);

    private String sectorSeleccionado = "Sector A";
    private double humedadSeleccionada = 35.0;
    private String observacionesSeleccionadas = "";

    public SimuladorRiego() {
        super("Simulador de Riego por Goteo - Gauss-Seidel");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        JLabel titulo = new JLabel("Simulador de Riego por Goteo y Balance Hidrico", SwingConstants.CENTER);
        titulo.setFont(new Font("SansSerif", Font.BOLD, 18));
        add(titulo, BorderLayout.NORTH);

        JPanel formulario = new JPanel(new GridBagLayout());
        formulario.setBorder(BorderFactory.createTitledBorder("Datos del riego"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 0;
        agregarCampo(formulario, c, 0, "Sector:", campoSector);
        agregarCampo(formulario, c, 1, "Caudal objetivo:", campoCaudal);
        agregarCampo(formulario, c, 2, "Humedad del suelo (%):", campoHumedad);
        c.gridx = 0; c.gridy = 3; c.anchor = GridBagConstraints.NORTHWEST;
        formulario.add(new JLabel("Observaciones:"), c);
        c.gridx = 1; c.weightx = 1;
        campoObservaciones.setLineWrap(true);
        campoObservaciones.setWrapStyleWord(true);
        formulario.add(new JScrollPane(campoObservaciones), c);

        JButton guardarCalcular = new JButton("Validar datos y calcular");
        c.gridx = 0; c.gridy = 4; c.gridwidth = 2; c.weightx = 1;
        formulario.add(guardarCalcular, c);

        JPanel lateral = new JPanel(new BorderLayout(5, 5));
        lateral.add(formulario, BorderLayout.NORTH);
        JPanel botones = new JPanel(new GridLayout(0, 1, 6, 6));
        JButton guardar = new JButton("Guardar estado actual");
        JButton restaurar = new JButton("Restaurar estado anterior");
        JButton fuga = new JButton("Registrar observacion como incidencia");
        JButton sector = new JButton("Recorrer sector de riego");
        JButton humedad = new JButton("Agregar validacion de humedad");
        JButton procesar = new JButton("Procesar humedad");
        JButton estructuras = new JButton("Ver estructuras y Big-O");
        JButton limpiar = new JButton("Limpiar pantalla");
        botones.add(guardar);
        botones.add(restaurar);
        botones.add(fuga);
        botones.add(sector);
        botones.add(humedad);
        botones.add(procesar);
        botones.add(estructuras);
        botones.add(limpiar);
        lateral.add(botones, BorderLayout.CENTER);
        add(lateral, BorderLayout.WEST);

        salida.setEditable(false);
        salida.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        salida.setText("Bienvenido/a al simulador.\n"
            + "Completa el formulario y pulsa 'Validar datos y calcular'.\n"
            + "Los campos ya tienen los valores de ejemplo solicitados.\n\n");
        add(new JScrollPane(salida), BorderLayout.CENTER);

        sectores.agregar("Sector A");
        sectores.agregar("Sector B");
        sectores.agregar("Sector C");
        historialFugas.agregar("Inicio: no hay incidencias registradas");

        guardarCalcular.addActionListener(e -> validarGuardarYCalcular());
        guardar.addActionListener(e -> guardarEstado());
        restaurar.addActionListener(e -> restaurarEstado());
        fuga.addActionListener(e -> registrarIncidencia());
        sector.addActionListener(e -> mostrarSector());
        humedad.addActionListener(e -> agregarHumedad());
        procesar.addActionListener(e -> procesarHumedad());
        estructuras.addActionListener(e -> mostrarEstructuras());
        limpiar.addActionListener(e -> salida.setText("Pantalla limpiada. Las estructuras conservan sus datos.\n"));

        pack();
        setMinimumSize(new Dimension(1050, 560));
        setLocationRelativeTo(null);
    }

    private void agregarCampo(JPanel panel, GridBagConstraints c, int fila, String etiqueta, JTextField campo) {
        c.gridy = fila; c.gridx = 0; c.gridwidth = 1; c.weightx = 0;
        panel.add(new JLabel(etiqueta), c);
        c.gridx = 1; c.weightx = 1;
        panel.add(campo, c);
    }

    
    private void validarGuardarYCalcular() {
        String sector = campoSector.getText().trim();
        String textoCaudal = campoCaudal.getText().trim().replace(',', '.');
        String textoHumedad = campoHumedad.getText().trim().replace(',', '.');
        String observaciones = campoObservaciones.getText().trim();

        if (sector.isEmpty()) {
            mostrarError("Escribe el nombre del sector.");
            campoSector.requestFocus();
            return;
        }

        final double caudalObjetivo;
        final double humedad;
        try {
            caudalObjetivo = Double.parseDouble(textoCaudal);
            if (!Double.isFinite(caudalObjetivo) || caudalObjetivo <= 0) {
                mostrarError("El caudal objetivo debe ser un numero mayor que cero.");
                campoCaudal.requestFocus();
                return;
            }
        } catch (NumberFormatException ex) {
            mostrarError("El caudal objetivo debe ser numerico, por ejemplo 12.");
            campoCaudal.requestFocus();
            return;
        }

        try {
            humedad = Double.parseDouble(textoHumedad);
            if (!Double.isFinite(humedad) || humedad < 0 || humedad > 100) {
                mostrarError("La humedad debe estar entre 0 y 100 por ciento.");
                campoHumedad.requestFocus();
                return;
            }
        } catch (NumberFormatException ex) {
            mostrarError("La humedad debe ser numerica, por ejemplo 35.");
            campoHumedad.requestFocus();
            return;
        }

        
        sectorSeleccionado = sector;
        humedadSeleccionada = humedad;
        observacionesSeleccionadas = observaciones.isEmpty() ? "Sin observaciones" : observaciones;

        
        caudales[0] = caudalObjetivo;
        caudales[1] = caudalObjetivo * (10.0 / 12.0);
        caudales[2] = caudalObjetivo * (8.0 / 12.0);

        
        sectores.agregar(sectorSeleccionado);
        
        colaHumedad.add(sectorSeleccionado + " | Humedad: " + humedadSeleccionada
            + "% | Observaciones: " + observacionesSeleccionadas);
    
        historialFugas.agregar("Formulario - " + sectorSeleccionado + ", caudal objetivo: "
            + caudalObjetivo + ", humedad: " + humedadSeleccionada + "% , observaciones: "
            + observacionesSeleccionadas);

        salida.append("\n--- FORMULARIO VALIDADO Y GUARDADO ---\n");
        salida.append("Sector: " + sectorSeleccionado + "\n");
        salida.append("Caudal objetivo ingresado: " + caudalObjetivo + "\n");
        salida.append("Humedad: " + humedadSeleccionada + "%\n");
        salida.append("Observaciones: " + observacionesSeleccionadas + "\n");
        salida.append("Vector usado por el calculo: " + Arrays.toString(caudales) + "\n");

        calcularPresiones();
    }

    private void mostrarError(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Revisar datos", JOptionPane.ERROR_MESSAGE);
    }

    private double[] gaussSeidel(double[][] a, double[] b, int maxIter, double tolerancia) {
        int n = b.length;
        double[] x = new double[n];
        for (int i = 0; i < n; i++) {
            if (a[i][i] == 0.0) throw new IllegalArgumentException("La diagonal de la matriz no puede tener ceros.");
        }
        for (int iter = 0; iter < maxIter; iter++) {
            double errorMaximo = 0.0;
            double[] anterior = x.clone();
            for (int i = 0; i < n; i++) {
                double suma = 0.0;
                for (int j = 0; j < n; j++) if (j != i) suma += a[i][j] * x[j];
                x[i] = (b[i] - suma) / a[i][i];
                errorMaximo = Math.max(errorMaximo, Math.abs(x[i] - anterior[i]));
            }
            historialPresion.agregar("Iteracion " + (iter + 1) + ": " + Arrays.toString(x));
            if (errorMaximo < tolerancia) break;
        }
        return x;
    }

    private void calcularPresiones() {
        try {
            pilaPresiones.push(ultimaPresion.clone());
            ultimaPresion = gaussSeidel(coeficientes, caudales, 100, 0.0001);
            salida.append("\n--- RESULTADOS DE GAUSS-SEIDEL ---\n");
            salida.append("Sector calculado: " + sectorSeleccionado + "\n");
            salida.append("Vector de caudales/terminos independientes: " + Arrays.toString(caudales) + "\n");
            salida.append("Presiones aproximadas: " + Arrays.toString(ultimaPresion) + "\n");
            salida.append("Humedad registrada: " + humedadSeleccionada + "%\n");
            salida.append("Tolerancia: 0.0001 | Maximo: 100 iteraciones\n");
            salida.append("Nota: la humedad se registra para seguimiento; el sistema lineal usa el vector de caudales.\n");
        } catch (IllegalArgumentException ex) {
            mostrarError(ex.getMessage());
        }
    }

    private void guardarEstado() {
        pilaPresiones.push(ultimaPresion.clone());
        salida.append("\nEstado guardado en la pila: " + Arrays.toString(ultimaPresion) + "\n");
    }

    private void restaurarEstado() {
        if (pilaPresiones.isEmpty()) salida.append("\nLa pila esta vacia. Primero calcula o guarda un estado.\n");
        else {
            ultimaPresion = pilaPresiones.pop();
            salida.append("\nEstado restaurado desde la pila: " + Arrays.toString(ultimaPresion) + "\n");
        }
    }

    private void registrarIncidencia() {
        String descripcion = observacionesSeleccionadas.isBlank()
            ? "Incidencia sin descripcion" : "Incidencia: " + observacionesSeleccionadas;
        historialFugas.agregar(descripcion);
        salida.append("\nIncidencia agregada a la lista doble: " + descripcion + "\n");
    }

    private void mostrarSector() {
        salida.append("\nSector del ciclo de riego: " + sectores.siguiente() + "\n");
    }

    private void agregarHumedad() {
        String valor = JOptionPane.showInputDialog(this,
            "Escribe una validacion de humedad para la cola:",
            sectorSeleccionado + " | Humedad: " + humedadSeleccionada + "%");
        if (valor != null && !valor.trim().isEmpty()) {
            colaHumedad.add(valor.trim());
            salida.append("\nValidacion agregada a la cola FIFO: " + valor.trim() + "\n");
        }
    }

    private void procesarHumedad() {
        if (colaHumedad.isEmpty()) salida.append("\nNo hay validaciones de humedad pendientes.\n");
        else salida.append("\nValidacion procesada: " + colaHumedad.remove() + "\n");
    }

    private void mostrarEstructuras() {
        salida.append("\n--- ESTRUCTURAS DE DATOS ---\n");
        salida.append("Vector de caudales: " + Arrays.toString(caudales) + "\n");
        salida.append("Matriz de coeficientes:\n");
        for (double[] fila : coeficientes) salida.append("  " + Arrays.toString(fila) + "\n");
        salida.append("Lista simple (aproximaciones): " + historialPresion + "\n");
        salida.append("Lista doble (incidencias/formularios): " + historialFugas + "\n");
        salida.append("Lista circular (sectores): " + sectores + "\n");
        salida.append("Pila: " + pilaPresiones.size() + " estado(s)\n");
        salida.append("Cola: " + colaHumedad.size() + " validacion(es) pendientes\n");
        salida.append("Big-O: Gauss-Seidel denso O(k*n^2); busqueda en lista O(n); pila O(1); cola O(1).\n");
    }

    static class ListaSimple {
        private static class Nodo {
            String dato; Nodo siguiente;
            Nodo(String dato) { this.dato = dato; }
        }
        private Nodo cabeza;
        void agregar(String dato) {
            Nodo nuevo = new Nodo(dato);
            if (cabeza == null) cabeza = nuevo;
            else { Nodo actual = cabeza; while (actual.siguiente != null) actual = actual.siguiente; actual.siguiente = nuevo; }
        }
        @Override public String toString() {
            StringBuilder sb = new StringBuilder("["); Nodo actual = cabeza;
            while (actual != null) { if (sb.length() > 1) sb.append(" -> "); sb.append(actual.dato); actual = actual.siguiente; }
            return sb.append("]").toString();
        }
    }

    static class ListaDoble {
        private static class Nodo {
            String dato; Nodo anterior, siguiente;
            Nodo(String dato) { this.dato = dato; }
        }
        private Nodo cabeza, cola;
        void agregar(String dato) {
            Nodo nuevo = new Nodo(dato);
            if (cabeza == null) cabeza = cola = nuevo;
            else { cola.siguiente = nuevo; nuevo.anterior = cola; cola = nuevo; }
        }
        @Override public String toString() {
            StringBuilder sb = new StringBuilder("["); Nodo actual = cabeza;
            while (actual != null) { if (sb.length() > 1) sb.append(" <-> "); sb.append(actual.dato); actual = actual.siguiente; }
            return sb.append("]").toString();
        }
    }

    static class ListaCircular {
        private static class Nodo {
            String dato; Nodo siguiente;
            Nodo(String dato) { this.dato = dato; }
        }
        private Nodo primero, ultimo, actual;
        void agregar(String dato) {
            Nodo nuevo = new Nodo(dato);
            if (primero == null) { primero = ultimo = actual = nuevo; nuevo.siguiente = primero; }
            else { ultimo.siguiente = nuevo; ultimo = nuevo; ultimo.siguiente = primero; }
        }
        String siguiente() {
            if (actual == null) return "No hay sectores";
            String dato = actual.dato; actual = actual.siguiente; return dato;
        }
        @Override public String toString() {
            if (primero == null) return "[]";
            StringBuilder sb = new StringBuilder("["); Nodo nodo = primero;
            do { if (sb.length() > 1) sb.append(" -> "); sb.append(nodo.dato); nodo = nodo.siguiente; } while (nodo != primero);
            return sb.append(" -> vuelve al inicio]").toString();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SimuladorRiego().setVisible(true));
    }
}
