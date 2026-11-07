package br.unicesumar.chat.client;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Ponto de entrada do cliente com interface gráfica.
 *
 * <p>Para testar com vários usuários, abra este programa uma vez para cada um
 * (cada janela é um cliente independente):
 * {@code java -cp target/chat.jar br.unicesumar.chat.client.ClienteGui}</p>
 */
public final class ClienteGui {

    private ClienteGui() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            instalarAparencia();

            AplicacaoChat aplicacao = new AplicacaoChat();
            JFrame janela = new JFrame("Chat Multiusuário");
            janela.setContentPane(aplicacao);
            janela.setIconImage(Tema.icone(64));
            janela.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            janela.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    aplicacao.encerrar(); // sai do chat de forma limpa antes de fechar
                    janela.dispose();
                    System.exit(0);
                }
            });
            janela.setMinimumSize(new Dimension(820, 520));
            janela.setSize(1020, 680);
            janela.setLocationRelativeTo(null);
            janela.setVisible(true);
        });
    }

    /** Nimbus dá uma aparência consistente em Windows, Linux e macOS; se faltar, usa o padrão. */
    private static void instalarAparencia() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    return;
                }
            }
        } catch (Exception ignorada) {
            // segue com a aparência padrão
        }
    }
}
