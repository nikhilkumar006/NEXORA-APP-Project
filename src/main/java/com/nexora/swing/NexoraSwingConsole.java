package com.nexora.swing;

import org.springframework.jdbc.core.JdbcTemplate;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * Optional Swing GUI required by the APP brief. It is deliberately opt-in so
 * the normal web server remains headless-friendly. Enable with NEXORA_SWING=true.
 */
@org.springframework.stereotype.Component
public class NexoraSwingConsole {
    private final JdbcTemplate jdbc;
    public NexoraSwingConsole(JdbcTemplate jdbc){this.jdbc=jdbc;}

    public void launch(){
        SwingUtilities.invokeLater(() -> {
            JFrame frame=new JFrame("NEXORA - Desktop Admin Console");
            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            frame.setSize(760,480);
            frame.setLocationRelativeTo(null);
            JTextArea output=new JTextArea(); output.setEditable(false); output.setFont(new Font(Font.MONOSPACED,Font.PLAIN,13));
            JButton refresh=new JButton("Refresh database summary");
            refresh.addActionListener(e -> refresh(output));
            JPanel top=new JPanel(new FlowLayout(FlowLayout.LEFT)); top.add(new JLabel("NEXORA JDBC Console")); top.add(refresh);
            frame.add(top,BorderLayout.NORTH); frame.add(new JScrollPane(output),BorderLayout.CENTER); frame.setVisible(true); refresh(output);
        });
    }
    private void refresh(JTextArea output){
        try{
            Integer students=jdbc.queryForObject("SELECT COUNT(*) FROM students",Integer.class);
            Integer evidence=jdbc.queryForObject("SELECT COUNT(*) FROM student_evidence",Integer.class);
            Integer projects=jdbc.queryForObject("SELECT COUNT(*) FROM student_projects",Integer.class);
            Integer achievements=jdbc.queryForObject("SELECT COUNT(*) FROM achievement_records",Integer.class);
            List<Map<String,Object>> bands=jdbc.queryForList("SELECT source_version,band,COUNT(*) criteria_count FROM career_criteria GROUP BY source_version,band ORDER BY source_version,band");
            StringBuilder b=new StringBuilder(); b.append("NEXORA DATABASE SUMMARY\n=======================\n");
            b.append("Students      : ").append(students).append('\n'); b.append("Evidence      : ").append(evidence).append('\n');
            b.append("Projects      : ").append(projects).append('\n'); b.append("Achievements  : ").append(achievements).append("\n\nCriteria loaded:\n");
            for(Map<String,Object> row:bands)b.append("- ").append(row.get("source_version")).append(" / ").append(row.get("band")).append(" = ").append(row.get("criteria_count")).append(" metrics\n");
            output.setText(b.toString());
        }catch(Exception ex){output.setText("Database check failed:\n"+ex.getMessage());}
    }
}
