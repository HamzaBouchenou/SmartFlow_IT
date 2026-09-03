import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import { AppLayout } from './layout/AppLayout';
import { AdminLayout } from './layout/AdminLayout';
import { ProtectedRoute } from './routes/ProtectedRoute';
import { LoginPage } from './pages/LoginPage';
import { HomePage } from './pages/HomePage';
import { ProfilePage } from './pages/ProfilePage';
import { CataloguePage } from './pages/CataloguePage';
import { ServiceRequestTypesPage } from './pages/ServiceRequestTypesPage';
import { NewRequestPage } from './pages/NewRequestPage';
import { MyRequestsPage } from './pages/MyRequestsPage';
import { RequestDetailPage } from './pages/RequestDetailPage';
import { TasksPage } from './pages/TasksPage';
import { DashboardPage } from './pages/DashboardPage';
import { NotificationsPage } from './pages/NotificationsPage';
import { AdminHomePage } from './pages/AdminHomePage';
import { AdminAuditLogPage } from './pages/AdminAuditLogPage';
import { AdminSystemParametersPage } from './pages/AdminSystemParametersPage';
import { AdminOrganizationPage } from './pages/AdminOrganizationPage';
import { AdminEmailTemplatesPage } from './pages/AdminEmailTemplatesPage';
import { AdminSlaPage } from './pages/AdminSlaPage';
import { AdminDiagnosticsPage } from './pages/AdminDiagnosticsPage';
import { AdminUsersPage } from './pages/AdminUsersPage';
import { AdminCataloguePage } from './pages/AdminCataloguePage';
import { AdminFormsPage } from './pages/AdminFormsPage';
import { AdminWorkflowsPage } from './pages/AdminWorkflowsPage';

/** §9.4 - écrans principaux. La protection réelle des données reste côté serveur
 * (RG-06, canAct) : ce routeur ne fait que masquer les écrans à un visiteur non connecté,
 * ce que §11.1 appelle explicitement insuffisant à lui seul. */
export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route
            element={
              <ProtectedRoute>
                <AppLayout />
              </ProtectedRoute>
            }
          >
            <Route index element={<HomePage />} />
            <Route path="/profil" element={<ProfilePage />} />
            <Route path="/catalogue" element={<CataloguePage />} />
            <Route path="/catalogue/:serviceId" element={<ServiceRequestTypesPage />} />
            <Route path="/mes-demandes" element={<MyRequestsPage />} />
            <Route path="/demandes/nouvelle/:requestTypeId" element={<NewRequestPage />} />
            <Route path="/demandes/:id" element={<RequestDetailPage />} />
            <Route path="/mes-taches" element={<TasksPage />} />
            <Route path="/tableau-de-bord" element={<DashboardPage />} />
            <Route path="/notifications" element={<NotificationsPage />} />
            {/* Route de mise en page sans chemin (§6.10, maquette 08) : elle n'ajoute
                aucune URL et n'en change aucune, elle donne seulement à ces onze écrans
                le rail de sous-navigation commun d'`AdminLayout`. */}
            <Route element={<AdminLayout />}>
              <Route path="/administration" element={<AdminHomePage />} />
              <Route path="/administration/journal-audit" element={<AdminAuditLogPage />} />
              <Route path="/administration/parametres" element={<AdminSystemParametersPage />} />
              <Route path="/administration/organisation" element={<AdminOrganizationPage />} />
              <Route path="/administration/modeles-email" element={<AdminEmailTemplatesPage />} />
              <Route path="/administration/sla" element={<AdminSlaPage />} />
              <Route path="/administration/diagnostic" element={<AdminDiagnosticsPage />} />
              <Route path="/administration/utilisateurs" element={<AdminUsersPage />} />
              <Route path="/administration/catalogue" element={<AdminCataloguePage />} />
              <Route path="/administration/formulaires" element={<AdminFormsPage />} />
              <Route path="/administration/workflows" element={<AdminWorkflowsPage />} />
            </Route>
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
